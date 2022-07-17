// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLatch;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests {@link JBCefJSQuery} for OSR browser.
 * See: IDEA-264004, JBR-3175
 *
 * @author tav
 */
public class JBCefJSQueryOSRTest {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.setRegistryProperty("ide.browser.jcef.osr.enabled", "true");
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test1() {
    JBCefClient client = JBCefApp.getInstance().createClient();
    client.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 1);

    JBCefOsrHandlerBrowser browser = JBCefOsrHandlerBrowser.create("", new MyRenderHandler(), client);
    JBCefJSQuery jsQuery = JBCefJSQuery.create(browser);

    doTest(browser, jsQuery);
  }

  @Test
  public void test2() {
    JBCefOsrHandlerBrowser browser = JBCefOsrHandlerBrowser.create("", new MyRenderHandler(), false);
    JBCefJSQuery jsQuery = JBCefJSQuery.create(browser);
    browser.createImmediately();

    doTest(browser, jsQuery);
  }

  @Test
  public void test3() {
    JBCefClient client = JBCefApp.getInstance().createClient();
    client.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 1);

    JBCefBrowser browser = JBCefBrowser.createBuilder()
      .setOffScreenRendering(true)
      .setClient(client)
      .setCreateImmediately(true)
      .build();

    JBCefJSQuery jsQuery = JBCefJSQuery.create((JBCefBrowserBase)browser);

    doTest(browser, jsQuery);
  }

  @Test
  public void test4() {
    JBCefBrowser browser = JBCefBrowser.createBuilder()
      .setOffScreenRendering(true)
      .build();

    JBCefJSQuery jsQuery = JBCefJSQuery.create((JBCefBrowserBase)browser);
    browser.createImmediately();

    doTest(browser, jsQuery);
  }

  public void doTest(@NotNull JBCefBrowserBase browser, @NotNull JBCefJSQuery jsQuery) {
    CountDownLatch latch = new CountDownLatch(1);

    jsQuery.addHandler(result -> {
      System.out.println("JBCefJSQuery result: " + result);
      latch.countDown();
      return null;
    });

    invokeAndWaitForLoad(browser, () -> {
      browser.loadURL("chrome:version");
    });

    invokeAndWaitForLatch(latch, () -> {
      System.out.println("Executing JBCefJSQuery...");
      browser.getCefBrowser().executeJavaScript(jsQuery.inject("'hello'"), "about:blank", 0);
    });
  }

  private static class MyRenderHandler implements CefRenderHandler {
    @Override
    public Rectangle getViewRect(CefBrowser browser) {
      return new Rectangle(0, 0, 100, 100);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
      return false;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
      return new Point(0, 0);
    }

    @Override
    public double getDeviceScaleFactor(CefBrowser browser) {
      return 1;
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
      return false;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
      return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
    }
  }
}
