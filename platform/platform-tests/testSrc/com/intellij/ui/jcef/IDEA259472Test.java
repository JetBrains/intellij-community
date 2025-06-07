// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.cef.network.CefRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;

import static com.intellij.ui.jcef.JBCefTestHelper.await;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests IDEA-259472
 * When two load requests are performed in a row, the second requests aborts the first one.
 * In that case the error page should not abort the second request.
 *
 * @author tav
 */
public class IDEA259472Test {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();
  }

  @After
  public void after() {
    TestScaleHelper.restoreSystemProperties();
  }

  @Test
  public void test() {
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);

    JBCefBrowser jbCefBrowser = new JBCefBrowser("https://maps.google.com"); // we need some heavy website, taking time to load
    jbCefBrowser.setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT);

    jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandler() {
      @Override
      public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        System.out.println("JBCefLoadHtmlTest.onLoadingStateChange: " + isLoading);
        if (!isLoading && latch1.getCount() < 1) {
          latch2.countDown();
        }
      }

      @Override
      public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
        System.out.println("JBCefLoadHtmlTest.onLoadStart: " + frame.getURL());
        if (frame.getURL().contains("google")) {
          jbCefBrowser.loadURL("https://www.jetbrains.com"); // we need some heavy website, taking time to load
        }
      }

      @Override
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        System.out.println("JBCefLoadHtmlTest.onLoadEnd: " + frame.getURL());
        if (frame.getURL().contains("jetbrains")) {
          latch1.countDown();
        }
      }

      @Override
      public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        System.out.println("JBCefLoadHtmlTest.onLoadError: " + failedUrl);
      }
    }, jbCefBrowser.getCefBrowser());

    invokeAndWaitForLoad(jbCefBrowser, () -> {
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(jbCefBrowser.getComponent());
      frame.setVisible(true);
    });

    await(latch1, "waiting onLoadEnd");

    await(latch2, "waiting onLoadingStateChange");

    assertThat(jbCefBrowser.getCefBrowser().getURL()).contains("jetbrains");
  }
}
