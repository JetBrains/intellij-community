// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.NonHeadlessRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TestRule;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests that a windowed and default OSR browser can co-exist.
 *
 * @author tav
 */
public class JBCefMultiRenderingModeTest {
  @Rule public TestRule nonHeadless = new NonHeadlessRule();
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.setRegistryProperty("ide.browser.jcef.osr.enabled", "true");
  }

  @After
  public void after() {
    TestScaleHelper.restoreRegistryProperties();
  }

  @Test
  public void test() {
    show(JBCefBrowser.createBuilder().setOffScreenRendering(false).setUrl("chrome:version").createBrowser());
    show(JBCefBrowser.createBuilder().setOffScreenRendering(true).setUrl("chrome:version").createBrowser());
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
