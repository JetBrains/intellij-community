// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.jcef.JBCefClient.Properties;
import com.intellij.ui.scale.TestScaleHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLatch;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests {@link JBCefClient#JS_QUERY_POOL_SIZE}.
 *
 * @author tav
 */
public class JBCefJSQueryPoolSizePropTest {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();
  }

  @Test
  public void test1() {
    test(client -> {
      client.setProperty(Properties.JS_QUERY_POOL_SIZE, 1);
      return null;
    });
  }

  @Test
  public void test2() {
    JBCefJSQueryPoolSizePropTest.test(client -> {
      client.setProperty(Properties.JS_QUERY_POOL_SIZE, Integer.MAX_VALUE); // stress test
      return null;
    });
  }

  public static void test(@NotNull Function<? super JBCefClient, Void> setProperty) {
    CountDownLatch latchBefore = new CountDownLatch(1);
    CountDownLatch latchAfter = new CountDownLatch(1);

    JBCefBrowser browser = new JBCefBrowser("chrome:version");
    setProperty.apply(browser.getJBCefClient());

    JBCefJSQuery jsQuery_before = JBCefJSQuery.create((JBCefBrowserBase)browser);
    jsQuery_before.addHandler(result -> {
      System.out.println("JBCefJSQuery [before] result: " + result);
      latchBefore.countDown();
      return null;
    });

    invokeAndWaitForLoad(browser, () -> {
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(browser.getComponent(), BorderLayout.CENTER);
      frame.setVisible(true);
    });

    JBCefJSQuery jsQuery_after = JBCefJSQuery.create((JBCefBrowserBase)browser);
    jsQuery_after.addHandler(result -> {
      System.out.println("JBCefJSQuery [after] result: " + result);
      latchAfter.countDown();
      return null;
    });

    invokeAndWaitForLatch(latchBefore, () -> {
      browser.getCefBrowser().executeJavaScript(jsQuery_before.inject("'query_before'"), "about:blank", 0);
    });

    invokeAndWaitForLatch(latchAfter, () -> {
      browser.getCefBrowser().executeJavaScript(jsQuery_after.inject("'query_after'"), "about:blank", 0);
    });
  }
}
