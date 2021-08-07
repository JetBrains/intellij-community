// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

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
    TestScaleHelper.assumeStandalone();
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test() {
    JBCefBrowser browser = new JBCefBrowser("chrome:version");

    JBCefJSQuery jsQuery = JBCefJSQuery.create(browser);
    jsQuery.addHandler(result -> {
      CALLBACL_COUNT.incrementAndGet();
      String str = "JBCefJSQuery result: " + result;
      System.out.println(str);
      browser.loadHTML("<html><body>" + str + "</body></html>");
      return null;
    });

    invokeAndWaitForLoad(browser, () -> {
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(browser.getComponent(), BorderLayout.CENTER);
      frame.setVisible(true);
    });

    invokeAndWaitForLoad(browser,
      () -> browser.getCefBrowser().executeJavaScript(jsQuery.inject("'hello'"), "about:blank", 0));

    TestCase.assertEquals("JS callback has been erroneously called on page reload", 1, CALLBACL_COUNT.get());
  }
}
