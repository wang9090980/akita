/*
 * Copyright 2011 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.alibaba.akita.io;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import com.alibaba.akita.exception.AkInvokeException;
import com.alibaba.akita.exception.AkServerStatusException;
import com.alibaba.akita.util.Log;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Http/Https Invoker
 * Get
 * Post(not idempotent) 
 * Put
 * Delete
 * @author zhe.yangz 2011-12-30 下午01:49:38
 */
public class HttpInvoker {
    private static String TAG = "HttpInvoker";
    private static String CHARSET = HTTP.UTF_8;
    
    private static ThreadSafeClientConnManager connectionManager;
    private static DefaultHttpClient client;
    
    static {
        init();
    }
    
    /**
     * init
     */
    private static void init() {
        
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(
                new Scheme("https", _FakeSSLSocketFactory.getSocketFactory(), 443));
        
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, "utf-8");
        HttpConnectionParams.setConnectionTimeout(params, 5000);
        HttpConnectionParams.setSoTimeout(params, 15000);
        params.setBooleanParameter("http.protocol.expect-continue", false);

        connectionManager = new ThreadSafeClientConnManager(params, schemeRegistry);
        client = new DefaultHttpClient(connectionManager, params);
        
        // enable gzip support in Request and Response. 
        client.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                if (!request.containsHeader("Accept-Encoding")) {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }
        });
        client.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                //Log.i("ContentLength", entity.getContentLength()+"");
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++) {
                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(
                                    new GzipDecompressingEntity(response.getEntity()));
                            return;
                        }
                    }
                }
            }
        });
        
    }
    
    public static String get(String url) 
    throws AkServerStatusException, AkInvokeException {
        Log.v(TAG, "get:" + url);

        String retString = null;
        try {
            HttpGet request = new HttpGet(url);
            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK
             || statusCode == HttpStatus.SC_CREATED
             || statusCode == HttpStatus.SC_ACCEPTED) {
                HttpEntity resEntity = response.getEntity();
                retString = (resEntity == null) ?
                    null : EntityUtils.toString(resEntity, CHARSET);
            } else {
                HttpEntity resEntity = response.getEntity();
                throw new AkServerStatusException(
                        response.getStatusLine().getStatusCode(),
                        EntityUtils.toString(resEntity, CHARSET));
            }
        } catch (ClientProtocolException cpe) {
            Log.e(TAG, cpe.toString(), cpe);
            throw new AkInvokeException(AkInvokeException.CODE_HTTP_PROTOCOL_ERROR,
                    cpe.toString(), cpe);
        } catch (IOException ioe) {
            Log.e(TAG, ioe.toString(), ioe);
            throw new AkInvokeException(AkInvokeException.CODE_CONNECTION_ERROR,
                    ioe.toString(), ioe);
        }

        Log.v(TAG, "response:" + retString);
        return retString;
    }

    public static String post(String url, ArrayList<NameValuePair> params)
            throws AkInvokeException, AkServerStatusException {
        //==log start
        Log.v(TAG, "post:" + url);
        if (params != null) {
            Log.v(TAG, "params:=====================");
            for (NameValuePair nvp : params) {
                Log.v(TAG, nvp.getName() + "=" + nvp.getValue());
            }
            Log.v(TAG, "params end:=====================");
        }
        //==log end

        String retString = null;

        try {
            HttpPost request = new HttpPost(url);
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, CHARSET);
            request.setEntity(entity);
            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK
             || statusCode == HttpStatus.SC_CREATED
             || statusCode == HttpStatus.SC_ACCEPTED) {
                HttpEntity resEntity = response.getEntity();
                retString = (resEntity == null) ?
                        null : EntityUtils.toString(resEntity, CHARSET);
            } else {
                HttpEntity resEntity = response.getEntity();
                throw new AkServerStatusException(
                        response.getStatusLine().getStatusCode(),
                        EntityUtils.toString(resEntity, CHARSET));
                
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString(), e);
            throw new AkInvokeException(
                    AkInvokeException.CODE_HTTP_PROTOCOL_ERROR, e.toString(), e);
        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString(), e);
            throw new AkInvokeException(
                    AkInvokeException.CODE_HTTP_PROTOCOL_ERROR, e.toString(), e);
        } catch (IOException e) {
            Log.e(TAG, e.toString(), e);
            throw new AkInvokeException(
                    AkInvokeException.CODE_CONNECTION_ERROR, e.toString(), e);
        } catch (ParseException e) {
            Log.e(TAG, e.toString(), e);
            throw new AkInvokeException(
                    AkInvokeException.CODE_PARSE_EXCEPTION, e.toString(), e);
        }

        Log.v(TAG, "response:" + retString);
        return retString;
    }
    
    public static String put(String url, HashMap<String, String> map) {
        return "";
    }
    
    public static String delete(String url) {
        return "";
    }

    private static final int DEFAULT_BUFFER_SIZE = 65536;
    private static byte[] retrieveImageData(InputStream inputStream, String imgUrl, int fileSize)
            throws IOException {

        // determine the image size and allocate a buffer
        //Log.d(TAG, "fetching image " + imgUrl + " (" +
        //        (fileSize <= 0 ? "size unknown" : Long.toString(fileSize)) + ")");
        BufferedInputStream istream = new BufferedInputStream(inputStream);

        try {
            if (fileSize <= 0) {
                android.util.Log.w(TAG,
                        "Server did not set a Content-Length header, will default to buffer size of "
                                + DEFAULT_BUFFER_SIZE + " bytes");
                ByteArrayOutputStream buf = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int bytesRead = 0;
                while (bytesRead != -1) {
                    bytesRead = istream.read(buffer, 0, DEFAULT_BUFFER_SIZE);
                    if (bytesRead > 0)
                        buf.write(buffer, 0, bytesRead);
                }
                return buf.toByteArray();
            } else {
                byte[] imageData = new byte[fileSize];

                int bytesRead = 0;
                int offset = 0;
                while (bytesRead != -1 && offset < fileSize) {
                    bytesRead = istream.read(imageData, offset, fileSize - offset);
                    offset += bytesRead;
                }
                return imageData;
            }
        } finally {
            // clean up
            try {
                istream.close();
                inputStream.close();
            } catch (Exception ignore) { }
        }
    }

    private static final int NUM_RETRIES = 3;
    private static final int DEFAULT_RETRY_SLEEP_TIME = 1000;

    /**
     * version 2 image download impl, use byte[] to decode.
     * NUM_RETRIES retry.
     * @param imgUrl
     * @param inSampleSize
     * @return
     * @throws AkServerStatusException
     * @throws AkInvokeException
     */
    public static Bitmap getBitmapFromUrl(String imgUrl, int inSampleSize)
    throws AkServerStatusException, AkInvokeException {
        Log.v(TAG, "getBitmapFromUrl:" + imgUrl);

        int timesTried = 1;

        while (timesTried <= NUM_RETRIES) {
            try {
                HttpGet request = new HttpGet(imgUrl);
                HttpResponse response = client.execute(request);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK
                        || statusCode == HttpStatus.SC_CREATED
                        || statusCode == HttpStatus.SC_ACCEPTED) {
                    HttpEntity resEntity = response.getEntity();
                    InputStream inputStream = resEntity.getContent();

                    byte[] imgBytes = retrieveImageData(
                            inputStream, imgUrl, (int)(resEntity.getContentLength()));
                    if (imgBytes == null) {
                        SystemClock.sleep(DEFAULT_RETRY_SLEEP_TIME);
                        continue;
                    }

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (inSampleSize > 0 && inSampleSize < 10) {
                        options.inSampleSize = inSampleSize;
                    } else {
                        if (imgBytes.length > 2000000) { // can be improved
                            options.inSampleSize = 4;
                        } else if (imgBytes.length > 500000) {
                            options.inSampleSize = 2;
                        } else {
                            options.inSampleSize = 0;
                        }
                    }

                    Bitmap bm = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length, options);
                    if (bm == null) {
                        SystemClock.sleep(DEFAULT_RETRY_SLEEP_TIME);
                        continue;
                    }
                    return bm;
                } else {
                    HttpEntity resEntity = response.getEntity();
                    throw new AkServerStatusException(
                            response.getStatusLine().getStatusCode(),
                            EntityUtils.toString(resEntity, CHARSET));
                }
            } catch (ClientProtocolException cpe) {
                Log.e(TAG, cpe.toString(), cpe);
                throw new AkInvokeException(AkInvokeException.CODE_HTTP_PROTOCOL_ERROR,
                        cpe.toString(), cpe);
            } catch (IOException ioe) {
                Log.e(TAG, ioe.toString(), ioe);
                throw new AkInvokeException(AkInvokeException.CODE_CONNECTION_ERROR,
                        ioe.toString(), ioe);
            }

        }

        return null;
    }

    /**
     * version 1 image download impl, use InputStream to decode.
     * @param imgUrl
     * @param inSampleSize
     * @return
     * @throws AkServerStatusException
     * @throws AkInvokeException
     */
    public static Bitmap getImageFromUrl(String imgUrl, int inSampleSize) 
    throws AkServerStatusException, AkInvokeException {
        Log.v(TAG, "getImageFromUrl:" + imgUrl);
        Bitmap bitmap = null;
        for (int cnt = 0; cnt < 3; cnt++) {
            try {
                HttpGet request = new HttpGet(imgUrl);
                HttpResponse response = client.execute(request);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK
                        || statusCode == HttpStatus.SC_CREATED
                        || statusCode == HttpStatus.SC_ACCEPTED) {
                    HttpEntity resEntity = response.getEntity();
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        if (inSampleSize > 0 && inSampleSize < 10) {
                            options.inSampleSize = inSampleSize;
                        } else {
                            options.inSampleSize = 0;
                        }
                        InputStream inputStream = resEntity.getContent();

                        // return BitmapFactory.decodeStream(inputStream);
                        // Bug on slow connections, fixed in future release.
                        bitmap = BitmapFactory.decodeStream(new FlushedInputStream(
                                inputStream), null, options);
                    } catch (Exception e) {
                        e.printStackTrace();  //TODO Just for test
                        // no op
                    }
                    break;
                } else {
                    HttpEntity resEntity = response.getEntity();
                    throw new AkServerStatusException(
                            response.getStatusLine().getStatusCode(),
                            EntityUtils.toString(resEntity, CHARSET));
                }
            } catch (ClientProtocolException cpe) {
                Log.e(TAG, cpe.toString(), cpe);
                throw new AkInvokeException(AkInvokeException.CODE_HTTP_PROTOCOL_ERROR,
                        cpe.toString(), cpe);
            } catch (IOException ioe) {
                Log.e(TAG, ioe.toString(), ioe);
                throw new AkInvokeException(AkInvokeException.CODE_CONNECTION_ERROR,
                        ioe.toString(), ioe);
            }
        }
        return bitmap;
    }
    
    /*
     * An InputStream that skips the exact number of bytes provided, unless it
     * reaches EOF.
     */
    private static class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public long skip(long n) throws IOException {
            long totalBytesSkipped = 0L;
            while (totalBytesSkipped < n) {
                long bytesSkipped = in.skip(n - totalBytesSkipped);
                if (bytesSkipped == 0L) {
                    int b = read();
                    if (b < 0) {
                        break; // we reached EOF
                    } else {
                        bytesSkipped = 1; // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        }
    }
    
}
