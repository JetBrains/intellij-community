// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import static com.intellij.ui.scale.TestScaleHelper.*;
import static junit.framework.TestCase.*;

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

  @After
  public void after() {
    restoreProperties();
  }

  @Test
  public void test() {
    assumeStandalone();

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
