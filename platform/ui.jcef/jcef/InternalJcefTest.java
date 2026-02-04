// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CompletableDeferredKt;
import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.misc.Utils;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class InternalJcefTest {
  private static final Logger LOG = Logger.getInstance(InternalJcefTest.class);
  private static final Boolean IS_DISABLED = Utils.getBoolean("ide.browser.jcef.out-of-process.startup_test.disabled");
  private static final int LOAD_TIMEOUT_SEC = Utils.getInteger("ide.browser.jcef.out-of-process.startup_test.timeout_sec", 60);

  @SuppressWarnings("HttpUrlsUsage")
  private static final String TEST_URL = "http://test.com/test.html";
  private static final String TEST_CONTENT = "<html><head><title>Test Title</title></head><body>Test!</body></html>";

  private CompletableDeferred<String> myTestResult;

  private volatile boolean myIsTestFinished = false;

  private Consumer<String> myOnTestFailed = null;
  private Runnable myOnTestSuccess = null;

  void setOnFailed(Consumer<String> onTestFailed) {
    this.myOnTestFailed = onTestFailed;
  }

  void setOnSuccess(Runnable onTestSuccess) {
    this.myOnTestSuccess = onTestSuccess;
  }

  boolean isTestFinished() { return myIsTestFinished; }

  void start() {
    myTestResult = checkBrowserCreation();
    if (myTestResult == null)
      return;

    myTestResult.invokeOnCompletion(cause -> {
      myIsTestFinished = true;
      final String testResultErr = myTestResult.getCompleted();
      if (testResultErr != null) {
        if (myOnTestFailed != null)
          myOnTestFailed.accept(testResultErr);
      } else if (myOnTestSuccess != null)
        myOnTestSuccess.run();
      return null;
    });
  }

  private static class TestResourceHandler extends CefResourceHandlerAdapter {
    private int myOffset = 0;
    private final String myContent;
    private final String myMimeType;

    TestResourceHandler(String content, String mimeType) {
      myContent = content;
      myMimeType = mimeType;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
      callback.Continue();
      return true;
    }

    @Override
    public void getResponseHeaders(
      CefResponse response, IntRef response_length, StringRef redirectUrl) {
      response_length.set(myContent.length());
      response.setMimeType(myMimeType);
      response.setStatus(200);
    }

    @SuppressWarnings({"SSBasedInspection", "ImplicitDefaultCharsetUsage"})
    @Override
    public boolean readResponse(
      byte[] data_out, int bytes_to_read, IntRef bytes_read, CefCallback callback) {
      int length = myContent.length();
      if (myOffset >= length) return false;

      // Extract up to |bytes_to_read| bytes from |content_|.
      int endPos = myOffset + bytes_to_read;
      String dataToSend = (endPos > length) ? myContent.substring(myOffset)
                                            : myContent.substring(myOffset, endPos);

      // Copy extracted bytes into |data_out| and set the read length to |bytes_read|.
      ByteBuffer result = ByteBuffer.wrap(data_out);
      result.put(dataToSend.getBytes());
      bytes_read.set(dataToSend.length());

      myOffset = endPos;
      return true;
    }
  }

  // Runs simple jcef test in background thread
  // Returns (async) null when test is OK (and the description of error otherwise)
  private static CompletableDeferred<String> checkBrowserCreation() {
    if (IS_DISABLED)
      return null;
    CompletableDeferred<String> result = CompletableDeferredKt.CompletableDeferred(null);
    final Runnable test = new Runnable() {
      private CefLoadHandler.ErrorCode errCode = CefLoadHandler.ErrorCode.ERR_NONE;
      private String errText = null;
      @Override
      public void run() {
        CefApp cefApp = JBCefApp.getInstance().getCefApp();
        if (cefApp == null) {
          result.complete("JBCefApp.getInstance().getCefApp() == null");
          return;
        }

        boolean isInitialized = false;
        try {
          final CountDownLatch latch = new CountDownLatch(1);
          cefApp.onInitialization(state -> latch.countDown());
          latch.await(LOAD_TIMEOUT_SEC, TimeUnit.SECONDS);
          if (latch.getCount() == 0)
            isInitialized = true;
        } catch (InterruptedException e) {
          LOG.error(e);
        }

        String errDesc = "";
        if (isInitialized) {
          CefClient client = cefApp.createClient();
          final CountDownLatch latchCreated = new CountDownLatch(1);
          client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public void onAfterCreated(CefBrowser browser) {
              latchCreated.countDown();
            }
          });
          final CountDownLatch latchLoad = new CountDownLatch(2);
          client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
              latchLoad.countDown();
            }

            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
              latchLoad.countDown();
            }

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
              errCode = errorCode;
              errText = errorText;
            }
          });
          client.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser,
                                                                       CefFrame frame,
                                                                       CefRequest request,
                                                                       boolean isNavigation,
                                                                       boolean isDownload,
                                                                       String requestInitiator,
                                                                       BoolRef disableDefaultHandling) {
              return new CefResourceRequestHandlerAdapter() {
                @Override
                public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
                  return new TestResourceHandler(TEST_CONTENT, "text/html");
                }
              };
            }
          });

          CefBrowser browser =
            JBCefBrowserBase.createOsrBrowser(JBCefOSRHandlerFactory.getInstance(), client, TEST_URL, null, null, null, true,
                                              new CefBrowserSettings());
          browser.createImmediately();
          try {
            latchCreated.await(LOAD_TIMEOUT_SEC / 2, TimeUnit.SECONDS);
            latchLoad.await(LOAD_TIMEOUT_SEC, TimeUnit.SECONDS);
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
          finally {
            client.dispose();
          }

          if (latchCreated.getCount() > 0) {
            errDesc = "Native CefBrowser wasn't created (onAfterCreated wasn't called).";
          } else {
            // Native CefBrowser was created. Check latchLoad.
            final int lc = (int)latchLoad.getCount();
            if (lc == 0) {
              result.complete(null);
              return;
            }

            if (lc == 2) errDesc = "Native CefBrowser was successfully created but onLoadStart wasn't called.";
            else if (lc == 1) errDesc = "Native CefBrowser was successfully created but onLoadEnd wasn't called.";

            if (errCode != CefLoadHandler.ErrorCode.ERR_NONE) {
              final String errLoad = String.format("Native CefBrowser was successfully created but onLoadError occurred, errCode=%s, errText=%s.", errCode, errText);
              if (!errDesc.isEmpty())
                errDesc += " ";
              errDesc += errLoad;
            }
          }
        } else
          errDesc = "CefApp wasn't initialized.";

        LOG.warn(String.format("Startup JCEF test is failed. Error: %s", errDesc));
        result.complete(errDesc);
      }
    };
    ApplicationManager.getApplication().executeOnPooledThread(test);
    return result;
  }
}
