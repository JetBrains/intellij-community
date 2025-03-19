// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.intellij.lang.annotations.Language;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import javax.swing.*;
import java.awt.event.InputMethodEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.AttributedString;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JBCefInputMethodTest {
  private static final boolean IS_ENABLED = Boolean.getBoolean("jcef.tests.input_method_test.enabled");

  @Language("HTML") final static String HTML_TEXT = """
    <html>
      <body>
        === JBCefInputMethodTest ===
        <br>
        <textarea id='text_area'></textarea>
        <script>
          let text_area = document.getElementById('text_area')
          text_area.focus()
          text_area.addEventListener('input', (event) => {
            ###CALLBACK_PLACEHOLDER###
            console.log(`input event: ${event.target.value}`)
          })
          document.addEventListener("input", (event) => {
            console.log(`global input event: ${event.target.value}`)
          })
        </script>
      </body>
    </html>
    """;

  final static String URL = "https://some.url";

  static final int STARTUP_TIMEOUT_SEC = 5;
  static final int CALLBACK_TIMEOUT_MS = 3000;

  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  JBCefBrowser browser;

  @Before
  public void before() {
    TestScaleHelper.setRegistryProperty("ide.browser.jcef.osr.enabled", "true");
    TestScaleHelper.assumeStandalone();
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();
  }


  // This test is too complicated to be stable
  // TODO: make a unit test for the input method adapter
  @Test
  @Disabled
  public void test() throws InterruptedException, InvocationTargetException {
    if (!IS_ENABLED)
      return;

    var startupWaiter = new CefLoadHandlerAdapter() {
      final CountDownLatch latch = new CountDownLatch(1);

      @Override
      public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        browser.setFocus(true);
        latch.countDown();
        super.onLoadEnd(browser, frame, httpStatusCode);
      }
    };

    var stringWaiter = new StringWaiter();

    SwingUtilities.invokeAndWait(() -> {
      browser = new JBCefBrowser();
      browser.getJBCefClient().addLoadHandler(startupWaiter, browser.myCefBrowser);
      JBCefJSQuery jsQuery = JBCefJSQuery.create((JBCefBrowserBase)browser);
      jsQuery.addHandler(result -> {
        stringWaiter.setValue(result);
        System.out.println("Text changed: '" + result + "'");
        return null;
      });
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(browser.getComponent());
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent e) {
          browser.loadHTML(HTML_TEXT.replaceAll("###CALLBACK_PLACEHOLDER###", jsQuery.inject("event.target.value")), URL);
        }
      });
      frame.setVisible(true);
    });

    assertTrue(startupWaiter.latch.await(STARTUP_TIMEOUT_SEC, TimeUnit.SECONDS));
    assertNotNull(browser);

    browser.getCefBrowser().setFocus(true);

    // Compose A
    SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().getUIComponent().dispatchEvent(makeEvent("a", false));
    });
    assertTrue(stringWaiter.wait("a", CALLBACK_TIMEOUT_MS));

    // Compose AB
    SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().getUIComponent().dispatchEvent(makeEvent("ab", false));
    });
    assertTrue(stringWaiter.wait("ab", CALLBACK_TIMEOUT_MS));

    // Commit
    SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().getUIComponent().dispatchEvent(makeEvent("committed_", true));
    });
    assertTrue(stringWaiter.wait("committed_", CALLBACK_TIMEOUT_MS));

    // Compose another
    SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().getUIComponent().dispatchEvent(makeEvent("ab", false));
    });
    assertTrue(stringWaiter.wait("committed_ab", CALLBACK_TIMEOUT_MS));
    // Commit
    SwingUtilities.invokeLater(() -> {
      browser.getCefBrowser().getUIComponent().dispatchEvent(makeEvent("committed2", true));
    });
    assertTrue(stringWaiter.wait("committed_committed2", CALLBACK_TIMEOUT_MS));
  }

  private InputMethodEvent makeEvent(String text, boolean commit) {
    return new InputMethodEvent(browser.getCefBrowser().getUIComponent(), InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, new AttributedString(text).getIterator(),
                                commit ? text.length() : 0, null, null);
  }

  private static class StringWaiter {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private String value = "";

    void setValue(String value) {
      lock.lock();
      try {
        this.value = value;
        condition.signal();
      }
      finally {
        lock.unlock();
      }
    }

    boolean wait(String expectedValue, int timeoutMs) {
      lock.lock();
      try {
        while (!value.equals(expectedValue)) {
          long startTimeMs = System.nanoTime() / 1000000;
          if (!condition.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            return false;
          }
          timeoutMs -= (System.nanoTime() / 1000000 - startTimeMs);
        }
        return true;
      }
      catch (InterruptedException ignored) {
      }
      finally {
        lock.unlock();
      }
      return false;
    }
  }
}
