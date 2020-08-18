// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import junit.framework.TestCase;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JBCefTestHelper {
  public static void loadAndWait(@NotNull JBCefBrowser browser, @NotNull Runnable loadAction) {
    CountDownLatch latch = new CountDownLatch(1);

    browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
        System.out.println("onLoadEnd on " + browser);
        latch.countDown();
        browser.getJBCefClient().removeLoadHandler(this, cefBrowser);
      }
    }, browser.getCefBrowser());

    loadAndWait(latch, loadAction);
  }

  public static void loadAndWait(@NotNull CountDownLatch latch, @NotNull Runnable loadAction) {
    loadAction.run();

    TestCase.assertTrue(wait(latch));
  }

  public static boolean wait(@NotNull CountDownLatch latch) {
    try {
      return latch.await(10, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
    return false;
  }
}
