// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.NonHeadlessRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.junit.*;
import org.junit.rules.TestRule;

/**
 * Tests "ide.browser.jcef.jsQueryPoolSize" reg key.
 *
 * @author tav
 */
public class JBCefJSQueryPoolSizeKeyTest {
  @Rule public TestRule nonHeadless = new NonHeadlessRule();
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.setSystemProperty("ide.browser.jcef.jsQueryPoolSize", "1");
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test1() {
    JBCefJSQueryPoolSizePropTest.test(b -> null);
  }
}
