// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.DisposableRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.util.concurrent.CountDownLatch;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLatch;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests IDEA-246306
 * A JS callback should be called on the browser instance which created it.
 *
 * @author tav
 */
public class IDEA246306Test {
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Rule
  public DisposableRule myDisposableRule = new DisposableRule();

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
    new MyBrowser(myDisposableRule.getDisposable());
    new MyBrowser(myDisposableRule.getDisposable());
  }

  static class MyBrowser extends JBCefBrowser {
    static final JBCefClient ourClient = JBCefApp.getInstance().createClient();

    final JBCefJSQuery myQuery = JBCefJSQuery.create((JBCefBrowserBase)this);
    final CountDownLatch latch = new CountDownLatch(1);
    private final Disposable myDisposable;

    @SuppressWarnings("ObjectToString")
    MyBrowser(Disposable disposable) {
      super(createBuilder().setClient(ourClient).setUrl("chrome:version"));
      myDisposable = disposable;
      myQuery.addHandler(result -> {
        System.out.println("query: result " + result + ", on " + this);
        if (!result.equals(this.toString())) {
          System.err.println("JS query called on wrong instance " + this);
        }
        latch.countDown();
        return null;
      });

      invokeAndWaitForLoad(this, () -> {
        JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
        Disposer.register(myDisposable, () -> frame.removeNotify());
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);
        frame.add(getComponent(), BorderLayout.CENTER);
        frame.setVisible(true);
      });

      invokeAndWaitForLatch(latch, "executeJavaScript -> wait js callback",
        () -> getCefBrowser().executeJavaScript(myQuery.inject("'" + this + "'"), getCefBrowser().getURL(), 0));
    }
  }
}
