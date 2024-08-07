// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;

import static com.intellij.ui.jcef.JBCefTestHelper.invokeAndWaitForLoad;

/**
 * Tests headless mode for an OSR browser.
 *
 * @author tav
 */
public class JBCefHeadlessOsrTest {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "true");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.setRegistryProperty("ide.browser.jcef.headless.enabled", "true");
    TestScaleHelper.setRegistryProperty("ide.browser.jcef.osr.enabled", "true");
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test() {
    JBCefBrowser browser = JBCefBrowser.createBuilder()
      .setOffScreenRendering(true)
      .setOSRHandlerFactory(new JBCefOSRHandlerFactory() {
         @Override
         @NotNull
         public JComponent createComponent(boolean isMouseWheelEventEnabled) {
          return new JBCefOsrComponent(isMouseWheelEventEnabled);
        }

         @Override
         @NotNull
         public CefRenderHandler createCefRenderHandler(@NotNull JComponent component) {
          assert component instanceof JBCefOsrComponent;
          JBCefOsrComponent osrComponent = (JBCefOsrComponent)component;
          JBCefOsrHandler handler = new JBCefOsrHandler();
          osrComponent.setRenderHandler(handler);
          return handler;
        }
      })
      .setUrl("chrome:version")
      .build();

    invokeAndWaitForLoad(browser, () -> browser.getCefBrowser().createImmediately());
  }
}
