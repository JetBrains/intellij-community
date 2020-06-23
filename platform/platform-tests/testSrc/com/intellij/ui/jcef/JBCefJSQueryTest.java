// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.util.ui.TestScaleHelper;
import junit.framework.TestCase;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Tests {@link JBCefClient#JBCEFCLIENT_JSQUERY_POOL_SIZE_PROP} and "ide.browser.jcef.jsQueryPoolSize" (used for testing purposes).
 *
 * @author tav
 */
public class JBCefJSQueryTest {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  static final CountDownLatch LATCH_JS_BEFORE = new CountDownLatch(1);
  static final CountDownLatch LATCH_JS_AFTER = new CountDownLatch(1);
  static final CountDownLatch LATCH_LOAD = new CountDownLatch(1);

  @Before
  public void before() {
    RegistryManager.getInstance().get("ide.browser.jcef.headless.enabled").setValue("true");
    TestScaleHelper.setSystemProperty("ide.browser.jcef.jsQueryPoolSize", "0");
  }

  @After
  public void after() {
    TestScaleHelper.restoreSystemProperties();
  }

  @Test
  public void test1() {
    TestScaleHelper.assumeStandalone();

    test(client -> {
      client.addProperty(JBCefClient.JBCEFCLIENT_JSQUERY_POOL_SIZE_PROP, 1);
      return null;
    });
  }

  @Test
  public void test2() {
    TestScaleHelper.assumeStandalone();

    System.setProperty("ide.browser.jcef.jsQueryPoolSize", "1");
    test(b -> null);
  }

  @Test
  public void test3() {
    TestScaleHelper.assumeStandalone();

    test(client -> {
      client.addProperty(JBCefClient.JBCEFCLIENT_JSQUERY_POOL_SIZE_PROP, Integer.MAX_VALUE); // stress test
      return null;
    });
  }

  public void test(@NotNull Function<JBCefClient, Void> setProperty) {
    JBCefBrowser browser = new JBCefBrowser("chrome:version");
    setProperty.apply(browser.getJBCefClient());

    browser.getJBCefClient().addLoadHandler(new CefLoadHandler() {
      @Override
      public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        System.out.println("JBCefLoadHtmlTest.onLoadingStateChange");
      }
      @Override
      public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
        System.out.println("JBCefLoadHtmlTest.onLoadStart");
      }
      @Override
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        System.out.println("JBCefLoadHtmlTest.onLoadEnd");
        LATCH_LOAD.countDown();
      }
      @Override
      public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        System.out.println("JBCefLoadHtmlTest.onLoadError");
      }
    }, browser.getCefBrowser());

    JBCefJSQuery jsQuery_before = JBCefJSQuery.create(browser);
    jsQuery_before.addHandler(result -> {
      System.out.println("JBCefJSQuery [before] result: " + result);
      LATCH_JS_BEFORE.countDown();
      return null;
    });

    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(browser.getComponent(), BorderLayout.CENTER);
      frame.setVisible(true);
    });

    wait(LATCH_LOAD);

    JBCefJSQuery jsQuery_after = JBCefJSQuery.create(browser);
    jsQuery_after.addHandler(result -> {
      System.out.println("JBCefJSQuery [after] result: " + result);
      LATCH_JS_AFTER.countDown();
      return null;
    });

    SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().executeJavaScript(jsQuery_before.inject("'query_before'"), "about:blank", 0);
    });

    wait(LATCH_JS_BEFORE);

    SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().executeJavaScript(jsQuery_after.inject("'query_after'"), "about:blank", 0);
    });

    wait(LATCH_JS_AFTER);
  }

  private static void wait(@NotNull CountDownLatch latch) {
    boolean success = false;
    try {
      success = latch.await(20, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
    TestCase.assertTrue(success);
  }
}
