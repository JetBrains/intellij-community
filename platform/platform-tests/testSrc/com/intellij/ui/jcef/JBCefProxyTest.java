// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import junit.framework.TestCase;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;

import static com.intellij.ui.jcef.JBCefTestHelper.await;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests proxy with authentication.
 *
 * @author tav
 */
public class JBCefProxyTest {
  // https://confluence.jetbrains.com/display/JBINT/HTTP+Proxy+with+authorization
  private static final @NotNull String PROXY_HOST = "proxy-auth-test.labs.intellij.net";
  private static final int PROXY_PORT = 3128;
  private static final @NotNull String LOGIN = "user1";
  private static final @NotNull String PASSWORD = "fg3W9";

  private volatile boolean passed;

  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @After
  public void after() {
    TestScaleHelper.restoreSystemProperties();
  }

  @Test
  public void test() {
    TestScaleHelper.assumeStandalone();

    JBCefProxySettings.setTestInstance(true,
                                       false,
                                       false,
                                       null,
                                       PROXY_HOST,
                                       PROXY_PORT,
                                       true,
                                       LOGIN,
                                       PASSWORD);

    CountDownLatch latch = new CountDownLatch(1);

    final String TEST_HOST = "ya.ru";
    JBCefBrowser jbCefBrowser = new JBCefBrowser("https://" + TEST_HOST);
    jbCefBrowser.setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT);

    jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        System.out.println("JBCefLoadHtmlTest.onLoadEnd: " + browser.getURL() + ", status: " + httpStatusCode);
        passed = httpStatusCode == 200;
        if (frame.getURL().contains(TEST_HOST)) {
          latch.countDown();
        }
      }
      @Override
      public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        passed = !failedUrl.contains(TEST_HOST);
        System.out.println("JBCefLoadHtmlTest.onLoadError: " + failedUrl + ", error: " + errorText);
      }
    }, jbCefBrowser.getCefBrowser());

    invokeAndWaitForLoad(jbCefBrowser, () -> {
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(jbCefBrowser.getComponent());
      frame.setVisible(true);
    });

    TestCase.assertTrue(await(latch));

    TestCase.assertTrue(passed);
  }
}