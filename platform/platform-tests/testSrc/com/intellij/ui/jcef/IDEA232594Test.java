// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.util.ui.TestScaleHelper;
import junit.framework.TestCase;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests https://youtrack.jetbrains.com/issue/IDEA-232594
 * A JS callback should not be called on page reload.
 *
 * @author tav
 */
public class IDEA232594Test {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  static final AtomicInteger CALLBACL_COUNT = new AtomicInteger(0);

  @Before
  public void before() {
    RegistryManager.getInstance().get("ide.browser.jcef.headless.enabled").setValue("true");
  }

  @After
  public void after() {
    TestScaleHelper.restoreSystemProperties();
  }

  @Test
  public void test() {
    TestScaleHelper.assumeStandalone();

    JBCefBrowser browser = new JBCefBrowser("chrome:version");

    JBCefJSQuery jsQuery = JBCefJSQuery.create(browser);
    jsQuery.addHandler(result -> {
      CALLBACL_COUNT.incrementAndGet();
      System.out.println("JBCefJSQuery result: " + result);
      browser.loadHTML("about:blank");
      return null;
    });

    loadAndWait(browser, () -> SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(browser.getComponent(), BorderLayout.CENTER);
      frame.setVisible(true);
    }));

    loadAndWait(browser, () -> SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().executeJavaScript(jsQuery.inject("'hello'"), "about:blank", 0);
    }));

    TestCase.assertEquals("JS callback has been erroneously called on page reload", 1, CALLBACL_COUNT.get());
  }

  private static void loadAndWait(@NotNull JBCefBrowser browser, @NotNull Runnable loadAction) {
    CountDownLatch latch = new CountDownLatch(1);

    browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
        System.out.println("onLoadEnd");
        latch.countDown();
        browser.getJBCefClient().removeLoadHandler(this, cefBrowser);
      }
    }, browser.getCefBrowser());

    loadAction.run();

    JBCefJSQueryTest.wait(latch);
  }
}
