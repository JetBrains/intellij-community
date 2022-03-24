// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLatch;
import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests https://youtrack.jetbrains.com/issue/IDEA-246306
 * A JS callback should be called on the browser instance which created it.
 *
 * @author tav
 */
public class IDEA246306Test {
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

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
    new MyBrowser();
    new MyBrowser();
  }

  static class MyBrowser extends JBCefBrowser {
    static final JBCefClient ourClient = JBCefApp.getInstance().createClient();

    final JBCefJSQuery myQuery = JBCefJSQuery.create((JBCefBrowserBase)this);
    final CountDownLatch latch = new CountDownLatch(1);

    @SuppressWarnings("ObjectToString")
    MyBrowser() {
      super(createBuilder().setClient(ourClient).setUrl("chrome:version"));
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
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);
        frame.add(getComponent(), BorderLayout.CENTER);
        frame.setVisible(true);
      });

      invokeAndWaitForLatch(latch,
        () -> getCefBrowser().executeJavaScript(myQuery.inject("'" + this + "'"), getCefBrowser().getURL(), 0));
    }
  }
}
