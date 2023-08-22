// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static com.intellij.ui.scale.TestScaleHelper.*;
import static org.junit.Assert.*;

/**
 * Tests ide.browser.jcef.headless.enabled and ide.browser.jcef.testMode.enabled registry keys
 * in headless mode.
 *
 * @author tav
 */
public class JBCefTestModeTest {
  static {
    setSystemProperty("java.awt.headless", "true");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    assumeStandalone();
  }

  @After
  public void after() {
    restoreProperties();
  }

  @Test
  public void test() {
    setRegistryProperty("ide.browser.jcef.headless.enabled", "false");
    setRegistryProperty("ide.browser.jcef.testMode.enabled", "false");

    assertFalse(JBCefApp.isSupported());

    try {
      JBCefApp.getInstance();
      fail();
    }
    catch (IllegalStateException ignore) {
    }

    setRegistryProperty("ide.browser.jcef.headless.enabled", "true");
    setRegistryProperty("ide.browser.jcef.testMode.enabled", "true");

    assertTrue(JBCefApp.isSupported());
    assertNotNull(JBCefApp.getInstance());
  }
}
