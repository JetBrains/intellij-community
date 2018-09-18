// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.TimeoutUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static java.net.HttpURLConnection.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpRequestsTest {
  private static final String LOCALHOST = "127.0.0.1";

  private HttpServer myServer;
  private String myUrl;

  @Before
  public void setUp() throws IOException {
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
  }

  @After
  public void tearDown() {
    myServer.stop(0);
  }

  @Test(timeout = 5000)
  public void redirectLimit() {
    try {
      HttpRequests.request("").redirectLimit(0).readString(null);
      fail();
    }
    catch (IOException e) {
      assertEquals(IdeBundle.message("error.connection.failed.redirects"), e.getMessage());
    }
  }

  @Test(timeout = 5000, expected = SocketTimeoutException.class)
  public void readTimeout() throws IOException {
    myServer.createContext("/", ex -> {
      TimeoutUtil.sleep(1000);
      ex.sendResponseHeaders(HTTP_OK, 0);
      ex.close();
    });

    HttpRequests.request(myUrl).readTimeout(50).readString(null);
    fail();
  }

  @Test(timeout = 5000)
  public void readContent() throws IOException {
    myServer.createContext("/", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/plain; charset=koi8-r");
      ex.sendResponseHeaders(HTTP_OK, 0);
      ex.getResponseBody().write("hello кодировочки".getBytes("koi8-r"));
      ex.close();
    });

    assertThat(HttpRequests.request(myUrl).readString(null)).isEqualTo("hello кодировочки");
  }

  @Test(timeout = 5000)
  public void gzippedContent() throws IOException {
    myServer.createContext("/", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/plain; charset=koi8-r");
      ex.getResponseHeaders().add("Content-Encoding", "gzip");
      ex.sendResponseHeaders(HTTP_OK, 0);
      try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(ex.getResponseBody())) {
        gzipOutputStream.write("hello кодировочки".getBytes("koi8-r"));
      }
      ex.close();
    });

    assertThat(HttpRequests.request(myUrl).readString(null)).isEqualTo("hello кодировочки");

    byte[] bytes = HttpRequests.request(myUrl).gzip(false).readBytes(null);
    assertThat(bytes).startsWith(0x1f, 0x8b);  // GZIP magic
  }

  @Test(timeout = 5000)
  public void tuning() throws IOException {
    myServer.createContext("/", ex -> {
      ex.sendResponseHeaders("HEAD".equals(ex.getRequestMethod()) ? HTTP_NO_CONTENT : HTTP_NOT_IMPLEMENTED, -1);
      ex.close();
    });

    assertEquals(HTTP_NO_CONTENT, HttpRequests.request(myUrl)
      .tuner((c) -> ((HttpURLConnection)c).setRequestMethod("HEAD"))
      .tryConnect());
  }

  @Test(timeout = 5000, expected = AssertionError.class)
  public void putNotAllowed() throws IOException {
    HttpRequests.request(myUrl)
                .tuner((c) -> ((HttpURLConnection)c).setRequestMethod("PUT"))
                .tryConnect();
    fail();
  }

  @Test(timeout = 5000)
  public void post() throws IOException {
    Ref<String> receivedData = Ref.create();
    myServer.createContext("/", ex -> {
      receivedData.set(StreamUtil.readText(ex.getRequestBody(), StandardCharsets.UTF_8));
      ex.sendResponseHeaders(HTTP_OK, -1);
      ex.close();
    });

    HttpRequests.post(myUrl, null).write("hello");
    assertThat(receivedData.get()).isEqualTo("hello");
  }

  @Test(timeout = 5000)
  public void postNotFound() throws IOException {
    myServer.createContext("/", ex -> {
      ex.sendResponseHeaders(HTTP_NOT_FOUND, -1);
      ex.close();
    });

    //noinspection SpellCheckingInspection
    try {
      HttpRequests
        .post(myUrl, null)
        .write("hello");
      fail();
    }
    catch (SocketException e) {
      // java.net.SocketException: Software caused connection abort: recv failed
      //noinspection SpellCheckingInspection
      assertThat(e.getMessage()).contains("recv failed");
    }
    catch (HttpRequests.HttpStatusException e) {
      assertThat(e.getMessage()).isEqualTo("Request failed with status code 404");
      assertThat(e.getStatusCode()).isEqualTo(HTTP_NOT_FOUND);
    }
  }

  @Test(timeout = 5000)
  public void postNotFoundWithResponse() throws IOException {
    String serverErrorText = "use another url";
    myServer.createContext("/", ex -> {
      byte[] bytes = serverErrorText.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(HTTP_UNAVAILABLE, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });

    try {
      HttpRequests
        .post(myUrl, null)
        .isReadResponseOnError(true)
        .write("hello");
      fail();
    }
    catch (HttpRequests.HttpStatusException e) {
      assertThat(e.getMessage()).isEqualTo(serverErrorText);
    }
  }

  @Test(timeout = 5000)
  public void notModified() throws IOException {
    myServer.createContext("/", ex -> {
      ex.sendResponseHeaders(HTTP_NOT_MODIFIED, -1);
      ex.close();
    });

    byte[] bytes = HttpRequests.request(myUrl).readBytes(null);
    assertThat(bytes).isEmpty();
  }

  @Test(timeout = 5000)
  public void permissionDenied() throws IOException {
    try {
      myServer.createContext("/", ex -> {
        ex.sendResponseHeaders(HTTP_UNAUTHORIZED, -1);
        ex.close();
      });

      HttpRequests.request(myUrl).productNameAsUserAgent().readString(null);
      fail();
    }
    catch (HttpRequests.HttpStatusException e) {
      assertThat(e.getStatusCode()).isEqualTo(HTTP_UNAUTHORIZED);
    }
  }

  @Test(timeout = 5000)
  public void invalidHeader() throws IOException {
    try {
      HttpRequests.request(myUrl).tuner(connection -> connection.setRequestProperty("X-Custom", "c-str\0")).readString(null);
      fail();
    }
    catch (AssertionError e) {
      assertThat(e.getMessage()).contains("value contains NUL bytes");
    }
  }
}