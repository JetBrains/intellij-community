// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.HeadlessRule;
import org.junit.*;
import org.junit.rules.TestRule;

import static com.intellij.ui.scale.TestScaleHelper.*;
import static junit.framework.TestCase.*;

/**
 * Tests ide.browser.jcef.headless.enabled and ide.browser.jcef.testMode.enabled registry keys
 * in headless mode.
 *
 * @author tav
 */
public class JBCefTestModeTest {
  @Rule public TestRule headless = new HeadlessRule();
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    assumeStandalone();
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
