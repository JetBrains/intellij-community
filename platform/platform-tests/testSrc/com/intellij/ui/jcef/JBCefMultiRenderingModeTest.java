// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests that a windowed and default OSR browser can co-exist.
 *
 * @author tav
 */
public class JBCefMultiRenderingModeTest {
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
  public void test() {
    show(JBCefBrowser.createBuilder().setOffScreenRendering(false).setUrl("chrome:version").build());
    show(JBCefBrowser.createBuilder().setOffScreenRendering(true).setUrl("chrome:version").build());
    Disposer.dispose(JBCefApp.getInstance().getDisposable());
  }

  private static void show(@NotNull JBCefBrowser browser) {
    invokeAndWaitForLoad(browser, () -> {
      JFrame frame = new JFrame(JBCefLoadHtmlTest.class.getName());
      frame.setSize(640, 480);
      frame.setLocationRelativeTo(null);
      frame.add(browser.getComponent(), BorderLayout.CENTER);
      frame.setVisible(true);
    });
  }
}
