// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class StartupTest {
  private static final Logger LOG = Logger.getInstance(StartupTest.class);
  private static final Boolean IS_DISABLED = Boolean.getBoolean("ide.browser.jcef.out-of-process.startup_test.disabled");

  @SuppressWarnings("HttpUrlsUsage")
  private static final String TEST_URL = "http://test.com/test.html";
  private static final String TEST_CONTENT = "<html><head><title>Test Title</title></head><body>Test!</body></html>";

  static class TestResourceHandler extends CefResourceHandlerAdapter {
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
  static void checkBrowserCreation() {
    if (IS_DISABLED)
      return;
    final Runnable test = new Runnable() {
      private CefLoadHandler.ErrorCode errCode = CefLoadHandler.ErrorCode.ERR_NONE;
      private String errText = null;
      @Override
      public void run() {
        CountDownLatch latch = new CountDownLatch(2);
        CefClient client = CefApp.getInstance().createClient();
        client.addLoadHandler(new CefLoadHandlerAdapter() {
          @Override
          public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
            latch.countDown();
          }
          @Override
          public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
            latch.countDown();
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

        CefBrowser browser = JBCefBrowserBase.createOsrBrowser(JBCefOSRHandlerFactory.getInstance(), client, TEST_URL, null, null, null, true, new CefBrowserSettings());
        browser.createImmediately();
        try {
          latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          LOG.error(e);
        }
        if (latch.getCount() > 0) {
          LOG.error(String.format("Startup JCEF test is failed (lc=%d), out-of-process mode is disabled.", (int)latch.getCount()));
          if (errCode != CefLoadHandler.ErrorCode.ERR_NONE)
            LOG.error(String.format("onLoadError occurred, errCode=%s, errText=%s.", errCode, errText));
          Registry.get(JBCefApp.REGISTRY_REMOTE_KEY).setValue(false);
          showNotification();
        }
      }
    };
    ApplicationManager.getApplication().executeOnPooledThread(test);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static void showNotification() {
    Notification notification = SettingsHelper.NOTIFICATION_GROUP.getValue().createNotification(
      "Out-of-process JCEF mode is disabled.",
      "JCEF is running in out-of-process mode now and it seems to be unstable. This mode will be disabled (JCEF will run in usual mode after IDE restart).",
      NotificationType.ERROR);

    notification.addAction(new AnAction(IdeBundle.message("notification.content.jcef.gpucrash.action.restart")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ApplicationManager.getApplication().restart();
      }
    });

    notification.notify(null);
  }
}
