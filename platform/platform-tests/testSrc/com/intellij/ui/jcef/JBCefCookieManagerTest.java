// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.cef.CefApp;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests {@link JBCefCookieManager} methods. See JBR-4102
 *
 * @author tav
 */
public class JBCefCookieManagerTest {
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  private static final @NotNull String TEST_URL = "http://wikipedia.org";

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test() {
    URL url;
    try {
      url = new URL(TEST_URL);
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
      fail("Unexpected exception");
      return;
    }
    String urlToQuery = url.getProtocol() + "://" + url.getHost();

    JBCefCookie cookie = new JBCefCookie(
      "MY_COOKIE",
      "MY_VALUE",
      url.getHost(),
      "/",
      false,
      false);

    JBCefBrowser browser = new JBCefBrowser(TEST_URL);

    CountDownLatch cefInitLatch = new CountDownLatch(1);
    CefApp.getInstance().onInitialization(s -> cefInitLatch.countDown());
    JBCefTestHelper.await(cefInitLatch, "waiting CefApp initialization");

    CountDownLatch loadStartedLatch = new CountDownLatch(1);
    browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
        System.out.println("onLoadStart");
        loadStartedLatch.countDown();
      }
    }, browser.getCefBrowser());

    JBCefCookieManager manager = JBCefBrowserBase.getGlobalJBCefCookieManager();

    /*
     * SET (before load)
     */
    System.out.println("Test setCookie()...");
    Future<Boolean> result = manager.setCookie(urlToQuery, cookie);
    JBCefTestHelper.showAsync(browser, JBCefCookieManagerTest.class.getSimpleName());
    testFuture(result, Function.identity());
    assertTrue(loadStartedLatch.getCount() > 0); // assure the cookie had been set before the site loading has started
    System.out.println("...done");

    // wait for the loading to start
    try {
      assertTrue(loadStartedLatch.await(5, TimeUnit.SECONDS));
    }
    catch (InterruptedException e) {
      e.printStackTrace();
      fail("Unexpected exception");
    }

    /*
     * GET
     */
    System.out.println("Test getCookie()...");
    Future<List<JBCefCookie>> result2 = manager.getCookies(urlToQuery, false);
    assertTrue(testFuture(result2, futureResult -> futureResult.contains(cookie)));
    System.out.println("...done");

    /*
     * DELETE
     */
    System.out.println("Test deleteCookies()...");
    Future<Boolean> result3 = manager.deleteCookies(urlToQuery, cookie.getName());
    assertTrue(testFuture(result3, Function.identity()));
    System.out.println("...done");

    /*
     * GET (to tineout)
     */
    System.out.println("Test getCookie() to timeout...");
    Future<List<JBCefCookie>> result4 = manager.getCookies(urlToQuery, false);
    assertTrue(testFuture(result4, null));
    System.out.println("...done");

    Disposer.dispose(browser);
  }

  private static <T> boolean testFuture(@NotNull Future<T> future, @Nullable Function<T, Boolean> test) {
    try {
      T result = future.get(1, TimeUnit.SECONDS);
      return test != null && test.apply(result);
    }
    catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      return false;
    }
    catch (TimeoutException e) {
      if (test != null) {
        e.printStackTrace();
        return false;
      }
      return true;
    }
  }
}
