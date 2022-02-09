// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static com.intellij.ui.jcef.JBCefTestHelper.*;

/**
 * IDEA-286693 a pool slot is not freed after JBCefJSQuery gets disposed
 *
 * @author tav
 */
public class JBCefJSQueryPoolSlotReuseTest {
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
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test() {
    var client = JBCefApp.getInstance().createClient();
    client.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 1);

    var browser = new JBCefBrowserBuilder().setUrl("about:blank").setClient(client).createBrowser();
    showAndWaitForLoad(browser, JBCefJSQueryPoolSlotReuseTest.class.getSimpleName());

    /*
     * Create and use a JS query.
     */
    var jsQuery1 = JBCefJSQuery.create((JBCefBrowserBase)browser);
    var funcName = jsQuery1.getFuncName();
    doTest(browser, jsQuery1);
    Disposer.dispose(jsQuery1); // should return the query function to the free slot

    /*
     * Make sure the query is invalid.
     */
    boolean isIllegal = false;
    try {
      jsQuery1.inject("");
    } catch (IllegalStateException ex) {
      isIllegal = true;
    }
    TestCase.assertTrue(isIllegal);

    /*
     * Create and use a JS query again and make sure it reuses the same function under the hood.
     */
    var jsQuery2 = JBCefJSQuery.create((JBCefBrowserBase)browser);
    TestCase.assertEquals(funcName, jsQuery2.getFuncName());
    doTest(browser, jsQuery2);

    Disposer.dispose(client);
  }

  void doTest(@NotNull JBCefBrowserBase browser, @NotNull JBCefJSQuery jsQuery) {
    var latch = new CountDownLatch(1);
    var expectedResult = "query hash is " + jsQuery.hashCode();

    jsQuery.addHandler(result -> {
      System.out.println("JBCefJSQuery result: " + result);
      TestCase.assertEquals(expectedResult, result);
      latch.countDown();
      return null;
    });

    invokeAndWaitForLatch(latch, () -> {
      String code = jsQuery.inject("'" + expectedResult + "'");
      System.out.println("Executing JBCefJSQuery: " + code);
      browser.getCefBrowser().executeJavaScript(code, browser.getCefBrowser().getURL(), 0);
    });
  }
}

