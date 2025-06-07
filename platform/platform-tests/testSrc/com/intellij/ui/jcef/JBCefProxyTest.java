// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.junit.*;

import javax.swing.*;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static com.intellij.ui.jcef.JBCefTestHelper.await;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class JBCefProxyTest {
  private static final String TEST_HOST = "captive.apple.com";

  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();

    var proxySettings = System.getProperty("idea.test.proxy.settings");
    assumeTrue("'idea.test.proxy.settings' not set", proxySettings != null);

    var matcher = Pattern.compile("(\\w+):([^@]+)@([^:]+):(\\d+)").matcher(proxySettings);  // 'user:pass@host:port'
    assertTrue("cannot parse proxy settings: '" + proxySettings + "'", matcher.matches() && matcher.groupCount() == 4);

    var proxyPort = Integer.parseInt(matcher.group(4));
    JBCefProxySettings.setTestInstance(true, false, false, false, null, matcher.group(3), proxyPort, null, true, matcher.group(1),
                                       matcher.group(2));
  }

  @After
  public void after() {
    TestScaleHelper.restoreSystemProperties();
  }

  @Test
  public void test() throws IOException {
    var latch = new CountDownLatch(1);
    var statusCode = new AtomicInteger(-1);

    var browser = new JBCefBrowser("https://" + TEST_HOST);
    browser.setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT);
    browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        System.out.println("JBCefProxyTest.onLoadEnd: " + browser.getURL() + ", status: " + httpStatusCode);
        if (frame.getURL().contains(TEST_HOST)) {
          statusCode.set(httpStatusCode);
          latch.countDown();
        }
      }

      @Override
      public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        System.out.println("JBCefProxyTest.onLoadError: " + failedUrl + ", error: " + errorText);
      }
    }, browser.getCefBrowser());

    invokeAndWaitForLoad(browser, () -> {
      var frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(browser.getComponent());
      frame.setVisible(true);
    });

    await(latch, "waiting onLoadEnd");

    assertEquals(200, statusCode.get());
  }
}
