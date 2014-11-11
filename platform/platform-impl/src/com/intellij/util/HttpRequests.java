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
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;

public final class HttpRequests {
  public static class HttpRequestBuilder {
    private final String url;
    private int connectTimeout = HttpConfigurable.CONNECTION_TIMEOUT;
    private int readTimeout = HttpConfigurable.CONNECTION_TIMEOUT;

    private boolean supportGzip;

    private HttpRequestBuilder(@NotNull String url) {
      this.url = url;
    }

    @NotNull
    public HttpRequestBuilder connectTimeout(int value) {
      connectTimeout = value;
      return this;
    }

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

    public <T> T get(@NotNull final ThrowableConvertor<URLConnection, T, Exception> handler) throws Exception {
      return loadData(this, handler);
    }
  }

  @NotNull
  public static HttpRequestBuilder request(@NotNull String url) {
    return new HttpRequestBuilder(url);
  }

  private static <T> T loadData(@NotNull final HttpRequestBuilder requestBuilder, @NotNull final ThrowableConvertor<URLConnection, T, Exception> handler) throws Exception {
    return ClassLoaderUtil.runWithClassLoader(new URLClassLoader(new URL[0], Thread.currentThread().getContextClassLoader()), new ThrowableComputable<T, Exception>() {
      @Override
      public T compute() throws Exception {
        URLConnection connection = openConnection(requestBuilder.url, requestBuilder.supportGzip).first;
        try {
          return handler.convert(connection);
        }
        finally {
          if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection)connection).disconnect();
          }
        }
      }
    });
  }

  @NotNull
  public static Pair<URLConnection, String> openConnection(@NotNull String initialUrl, boolean supportGzip) throws IOException {
    return openConnection(request(initialUrl).supportGzip(supportGzip));
  }

  @NotNull
  private static Pair<URLConnection, String> openConnection(@NotNull HttpRequestBuilder requestBuilder) throws IOException {
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
      return Pair.create(connection, url == requestBuilder.url ? null : url);
    }
    throw new IOException("Infinite redirection");
  }
}