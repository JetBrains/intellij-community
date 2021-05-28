// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JBCefTestHelper {
  public static void invokeAndWaitForLoad(@NotNull JBCefBrowserBase browser, @NotNull Runnable runnable) {
    CountDownLatch latch = new CountDownLatch(1);

    browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
        System.out.println("onLoadEnd on " + browser + " for " + browser.getCefBrowser().getURL());
        browser.getJBCefClient().removeLoadHandler(this, cefBrowser);
        latch.countDown();
      }
    }, browser.getCefBrowser());

    invokeAndWaitForLatch(latch, runnable);
  }

  public static void invokeAndWaitForLatch(@NotNull CountDownLatch latch, @NotNull Runnable runnable) {
    UIUtil.invokeLaterIfNeeded(runnable);

    TestCase.assertTrue(await(latch));
  }

  public static boolean await(@NotNull CountDownLatch latch) {
    try {
      return latch.await(5, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
    return false;
  }
}
