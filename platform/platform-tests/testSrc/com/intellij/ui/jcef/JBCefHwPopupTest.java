// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.TestScaleHelper;
import junit.framework.TestCase;
import org.junit.*;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.ui.jcef.JBCefTestHelper.*;

/**
 * Tests IDEA-244182 JCEF heavyweight popup closes too early or always stays on top [Linux]
 *
 * @author tav
 */
public class JBCefHwPopupTest {
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
    TestScaleHelper.restoreSystemProperties();
  }

  @Test
  public void test() throws InterruptedException, InvocationTargetException {
    JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
    frame.setSize(640, 480);
    frame.setLocationRelativeTo(null);
    invokeAndWaitForCondition(() -> frame.setVisible(true), () -> frame.isShowing());

    var browser = new JBCefBrowser();
    browser.loadHTML("<html><body><h1>Hello World</h1></body></html>");
    var comp = browser.getComponent();
    comp.setPreferredSize(new Dimension(200, 100));

    var popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(comp, comp)
      .setShowBorder(false)
      .setShowShadow(false)
      .setRequestFocus(true)
      .setCancelOnWindowDeactivation(true)
      .createPopup();

    invokeAndWaitForLoad(browser, () -> popup.show(new RelativePoint(frame, new Point(64, 48))));

    //
    // Check the popup has not closed immediately
    //
    var popupIsShowing = new AtomicBoolean(false);
    EventQueue.invokeAndWait(() -> {
      popupIsShowing.set(popup.getContent().isShowing());
    });

    TestCase.assertTrue(popupIsShowing.get());
  }
}
