// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import junit.framework.TestCase;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;

import static com.intellij.ui.jcef.JBCefTestHelper.await;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests https://youtrack.jetbrains.com/issue/IDEA-261496
 * When loading is canceled and then another loading is immediately started, the error page should not be displayed.
 *
 * @author tav
 */
public class IDEA261496Test {
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

    CountDownLatch latch = new CountDownLatch(1);

    JBCefBrowser jbCefBrowser = new JBCefBrowser("http://maps.google.com"); // heavy page
    jbCefBrowser.setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT);

    jbCefBrowser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
        System.out.println("JBCefLoadHtmlTest.onLoadStarted: " + browser.getURL());
        if (browser.getURL().contains("google")) {
          // cancel loading and start another one
          jbCefBrowser.getCefBrowser().stopLoad();
          jbCefBrowser.loadURL("chrome:version");
        }
      }
      @Override
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        System.out.println("JBCefLoadHtmlTest.onLoadEnd: " + browser.getURL());
        if (frame.getURL().contains("version")) {
          latch.countDown();
        }
      }
      @Override
      public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        System.out.println("JBCefLoadHtmlTest.onLoadError: " + browser.getURL());
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

    TestCase.assertTrue(jbCefBrowser.getCefBrowser().getURL().contains("version"));
  }
}
