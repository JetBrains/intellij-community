/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.errorreport.crash;

import com.intellij.openapi.project.IndexNotReadyException;
import groovy.json.internal.Charsets;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class GoogleCrashTest {
  public static final String STACK_TRACE =
    "\tat com.intellij.util.indexing.FileBasedIndexImpl.handleDumbMode(FileBasedIndexImpl.java:853)\n" +
    "\tat com.intellij.util.indexing.FileBasedIndexImpl.ensureUpToDate(FileBasedIndexImpl.java:802)\n" +
    "\tat com.intellij.psi.stubs.StubIndexImpl.processElements(StubIndexImpl.java:238)\n" +
    "\tat com.intellij.psi.stubs.StubIndex.process(StubIndex.java:76)\n" +
    "\tat com.intellij.psi.stubs.StubIndex.getElements(StubIndex.java:144)\n";

  private static final String SAMPLE_EXCEPTION =
    "com.intellij.openapi.project.IndexNotReadyException: Please change caller according to com.intellij.openapi.project.IndexNotReadyException documentation\n" +
    STACK_TRACE;

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private static final Throwable ourIndexNotReadyException = createExceptionFromDesc(SAMPLE_EXCEPTION, new IndexNotReadyException());

  // Most of the tests do a future.get(), but since they are uploaded to a local server, they should complete relatively quickly.
  // This rule enforces a shorter timeout for the tests. If you are debugging, you probably want to comment this out.
  @Rule
  public TestRule myTimeout = new Timeout(15, TimeUnit.SECONDS);

  private GoogleCrash myReporter;
  private LocalTestServer myTestServer;

  @Before
  public void setup() throws Exception {
    int port = getFreePort();
    assertTrue("Could not obtain free port", port > 0);
    myTestServer = new LocalTestServer(port);
    myTestServer.start();
    myReporter = new GoogleCrash("http://localhost:" + port + "/submit");
  }

  @After
  public void tearDown() {
    myTestServer.stop();
  }

  @Test
  public void checkServerReceivesPostedData() throws Exception {
    String expectedReportId = "deadcafe";
    Map<String,String> attributes = new ConcurrentHashMap<>();

    myTestServer.setResponseSupplier(httpRequest -> {
      if (httpRequest.method() != HttpMethod.POST) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
      }

      HttpPostRequestDecoder requestDecoder = new HttpPostRequestDecoder(httpRequest);
      try {
        for (InterfaceHttpData httpData : requestDecoder.getBodyHttpDatas()) {
          if (httpData instanceof Attribute) {
            Attribute attr = (Attribute)httpData;
            attributes.put(attr.getName(), attr.getValue());
          }
        }
      }
      catch (IOException e) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
      }
      finally {
        requestDecoder.destroy();
      }

      return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                         HttpResponseStatus.OK,
                                         Unpooled.wrappedBuffer(expectedReportId.getBytes(Charsets.UTF_8)));
    });

    CrashReport report = CrashReport.Builder.createForException(ourIndexNotReadyException)
      .setProduct("AndroidStudioTestProduct")
      .setVersion("1.2.3.4")
      .build();
    CompletableFuture<String> reportId = myReporter.submit(report);

    assertEquals(expectedReportId, reportId.get());

    // assert that the server get the expected data
    assertEquals("AndroidStudioTestProduct", attributes.get(GoogleCrash.KEY_PRODUCT_ID));
    assertEquals("1.2.3.4", attributes.get(GoogleCrash.KEY_VERSION));

    // Note: the exception message should have been elided
    assertEquals("com.intellij.openapi.project.IndexNotReadyException: <elided>\n" + STACK_TRACE,
                 attributes.get(GoogleCrash.KEY_EXCEPTION_INFO));

    List<String> descriptions = Arrays.asList("2.3.0.0\n1.8.0_73-b02",
                                              "2.3.0.1\n1.8.0_73-b02");
    report = CrashReport.Builder.createForCrashes(descriptions)
      .setProduct("AndroidStudioTestProduct")
      .setVersion("1.2.3.4")
      .build();

    attributes.clear();

    reportId = myReporter.submit(report);
    assertEquals(expectedReportId, reportId.get());

    // check that the crash count and descriptions made through
    assertEquals(descriptions.size(), Integer.parseInt(attributes.get("numCrashes")));
    assertEquals("2.3.0.0\n1.8.0_73-b02\n\n2.3.0.1\n1.8.0_73-b02", attributes.get("crashDesc"));
  }

  @Test(expected = ExecutionException.class)
  public void checkServerErrorCaptured() throws Exception {
    myTestServer.setResponseSupplier(httpRequest -> new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
    CrashReport report = CrashReport.Builder.createForException(ourIndexNotReadyException)
      .setProduct("AndroidStudioTestProduct")
      .setVersion("1.2.3.4")
      .build();
    myReporter.submit(report).get();
    fail("The above get call should have failed");
  }

  private static int getFreePort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
    catch (IOException e) {
      return -1;
    }
  }

  // Performs a real upload to staging
  public static void main(String[] args) {
    GoogleCrash crash = GoogleCrash.getInstance();

    CompletableFuture<String> response = crash.submit(CrashReport.Builder.createForException(ourIndexNotReadyException).build());
    try {
      String reportId = response.get(20, TimeUnit.SECONDS);
      System.out.println("View report at http://go/crash-staging/" + reportId);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Copied from RenderErrorPanelTest
  @SuppressWarnings("ThrowableInstanceNeverThrown")
  private static Throwable createExceptionFromDesc(String desc, @Nullable Throwable throwable) {
    // First line: description and type
    Iterator<String> iterator = Arrays.asList(desc.split("\n")).iterator(); // Splitter.on('\n').split(desc).iterator();
    assertTrue(iterator.hasNext());
    final String first = iterator.next();
    assertTrue(iterator.hasNext());
    String message = null;
    String exceptionClass;
    int index = first.indexOf(':');
    if (index != -1) {
      exceptionClass = first.substring(0, index).trim();
      message = first.substring(index + 1).trim();
    } else {
      exceptionClass = first.trim();
    }

    if (throwable == null) {
      try {
        @SuppressWarnings("unchecked")
        Class<Throwable> clz = (Class<Throwable>)Class.forName(exceptionClass);
        if (message == null) {
          throwable = clz.newInstance();
        } else {
          Constructor<Throwable> constructor = clz.getConstructor(String.class);
          throwable = constructor.newInstance(message);
        }
      } catch (Throwable t) {
        if (message == null) {
          throwable = new Throwable() {
            @Override
            public String getMessage() {
              return first;
            }

            @Override
            public String toString() {
              return first;
            }
          };
        } else {
          throwable = new Throwable(message);
        }
      }
    }

    List<StackTraceElement> frames = new ArrayList<StackTraceElement>();
    Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
    Pattern innerPattern = Pattern.compile("(.*):(\\d*)");
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.isEmpty()) {
        break;
      }
      Matcher outerMatcher = outerPattern.matcher(line);
      if (!outerMatcher.matches()) {
        fail("Line " + line + " does not match expected stactrace pattern");
      } else {
        String clz = outerMatcher.group(1);
        String method = outerMatcher.group(2);
        String inner = outerMatcher.group(3);
        if (inner.equals("Native Method")) {
          frames.add(new StackTraceElement(clz, method, null, -2));
        } else if (inner.equals("Unknown Source")) {
          frames.add(new StackTraceElement(clz, method, null, -1));
        } else {
          Matcher innerMatcher = innerPattern.matcher(inner);
          if (!innerMatcher.matches()) {
            fail("Trace parameter list " + inner + " does not match expected pattern");
          } else {
            String file = innerMatcher.group(1);
            int lineNum = Integer.parseInt(innerMatcher.group(2));
            frames.add(new StackTraceElement(clz, method, file, lineNum));
          }
        }
      }
    }

    throwable.setStackTrace(frames.toArray(new StackTraceElement[frames.size()]));

    // Dump stack back to string to make sure we have the same exception
    assertEquals(desc, getStackTrace(throwable));

    return throwable;
  }

  @NotNull
  private static String getStackTrace(@NotNull Throwable t) {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter);
    try {
      t.printStackTrace(writer);
      return stringWriter.toString();
    }
    finally {
      writer.close();
    }
  }
}
