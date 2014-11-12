/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

/**
 * GZip supported by default, so, you must use {@link #getInputStream(java.net.URLConnection)} to get connection input stream.
 */
public final class HttpRequests {
  public static class HttpRequestBuilder {
    private final String url;
    private int connectTimeout = HttpConfigurable.CONNECTION_TIMEOUT;
    private int readTimeout = HttpConfigurable.CONNECTION_TIMEOUT;

    private Consumer<String> effectiveUrlConsumer;

    private boolean supportGzip = true;

    private HttpRequestBuilder(@NotNull String url) {
      this.url = url;
    }

    @NotNull
    public HttpRequestBuilder connectTimeout(int value) {
      connectTimeout = value;
      return this;
    }

    @SuppressWarnings("unused")
    @NotNull
    public HttpRequestBuilder readTimeout(int value) {
      readTimeout = value;
      return this;
    }

    @NotNull
    public HttpRequestBuilder supportGzip(boolean value) {
      supportGzip = value;
      return this;
    }

    @NotNull
    public HttpRequestBuilder effectiveUrlConsumer(Consumer<String> value) {
      effectiveUrlConsumer = value;
      return this;
    }

    public <T, E extends Throwable> T get(@NotNull final ThrowableConvertor<URLConnection, T, E> handler) throws E, IOException {
      return loadData(this, handler);
    }
  }

  @NotNull
  public static HttpRequestBuilder request(@NotNull String url) {
    return new HttpRequestBuilder(url);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @NotNull
  public static InputStream getInputStream(@NotNull URLConnection connection) throws IOException {
    InputStream inputStream = connection.getInputStream();
    if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
      try {
        return new GZIPInputStream(inputStream);
      }
      catch (IOException e) {
        inputStream.close();
        throw e;
      }
    }
    else {
      return inputStream;
    }
  }

  private static <T, E extends Throwable> T loadData(@NotNull HttpRequestBuilder requestBuilder, @NotNull ThrowableConvertor<URLConnection, T, E> handler)
    throws E, IOException {
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], oldClassLoader));
    try {
      URLConnection connection = openConnection(requestBuilder);
      try {
        return handler.convert(connection);
      }
      finally {
        if (connection instanceof HttpURLConnection) {
          ((HttpURLConnection)connection).disconnect();
        }
      }
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  @NotNull
  private static URLConnection openConnection(@NotNull HttpRequestBuilder requestBuilder) throws IOException {
    int i = 0;
    String url = requestBuilder.url;
    while (i++ < 99) {
      URLConnection connection;
      if (ApplicationManager.getApplication() == null) {
        connection = new URL(url).openConnection();
      }
      else {
        connection = HttpConfigurable.getInstance().openConnection(url);
      }

      connection.setConnectTimeout(requestBuilder.connectTimeout);
      connection.setReadTimeout(requestBuilder.readTimeout);

      if (requestBuilder.supportGzip) {
        connection.setRequestProperty("Accept-Encoding", "gzip");
      }

      connection.setUseCaches(false);

      if (connection instanceof HttpURLConnection) {
        int responseCode = ((HttpURLConnection)connection).getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
          if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            url = connection.getHeaderField("Location");
          }
          else {
            url = null;
          }

          if (url == null) {
            throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
          }
          else {
            ((HttpURLConnection)connection).disconnect();
            continue;
          }
        }
      }

      if (url != requestBuilder.url && requestBuilder.effectiveUrlConsumer != null) {
        requestBuilder.effectiveUrlConsumer.consume(url);
      }
      return connection;
    }
    throw new IOException("Infinite redirection");
  }
}