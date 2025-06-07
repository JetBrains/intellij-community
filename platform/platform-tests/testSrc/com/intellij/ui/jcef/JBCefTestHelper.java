// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.misc.Utils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class JBCefTestHelper {
  // NOTE: TeamCity runs tests in parallel with downloading (and other processes), and because of that
  // first CEF initialization takes a long time (more than 15 sec in 1% of test runs). So use a large constant here.
  //
  private static int WAIT_BROWSER_SECONDS = Utils.getInteger("JCEF_WAIT_BROWSER_SECONDS", 60);
  /**
   * Shows the browser in a frame in waits for a load completion.
   */
  public static void showAndWaitForLoad(@NotNull JBCefBrowserBase browser, @NotNull String frameTitle) {
    invokeAndWaitForLoad(browser, () -> show(browser, frameTitle));
  }

  /**
   * Shows the browser in a frame asynchronously.
   */
  public static void showAsync(@NotNull JBCefBrowserBase browser, @NotNull String frameTitle) {
    EventQueue.invokeLater(() -> show(browser, frameTitle));
  }

  private static void show(@NotNull JBCefBrowserBase browser, @NotNull String frameTitle) {
    JFrame frame = new JFrame(frameTitle);
    frame.setSize(640, 480);
    frame.setLocationRelativeTo(null);
    frame.add(browser.getComponent(), BorderLayout.CENTER);
    frame.setVisible(true);
  }

  /**
   * Invokes and waits for a load completion. Either the runnable should load URL/HTML or the browser should be created with initial URL/HTML.
   */
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

    invokeAndWaitForLatch(latch, "waiting onLoadEnd", runnable);
  }

  /**
   * Invokes and waits for the condition to become true.
   */
  public static void invokeAndWaitForCondition(@NotNull Runnable runnable, @NotNull BooleanSupplier condition, @NotNull String description) {
    CountDownLatch latch = new CountDownLatch(1);

    invokeAndWaitForLatch(latch, description, () -> {
      runnable.run();
      latch.countDown();
    });

    try {
      while (!condition.getAsBoolean()) {
        //noinspection BusyWait
        Thread.sleep(100);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static void invokeAndWaitForLatch(@NotNull CountDownLatch latch, @NotNull String description, @NotNull Runnable runnable) {
    UIUtil.invokeLaterIfNeeded(runnable);
    await(latch, description);
  }

  public static void await(@NotNull CountDownLatch latch, @NotNull String description) {
    try {
      if (!latch.await(WAIT_BROWSER_SECONDS, TimeUnit.SECONDS)) {
        Assert.fail(description + " failed by timeout:\n" + ThreadDumper.dumpThreadsToString());
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
