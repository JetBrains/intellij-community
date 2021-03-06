// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests "ide.browser.jcef.jsQueryPoolSize" reg key.
 *
 * @author tav
 */
public class JBCefJSQueryPoolSizeKeyTest {
  static {
    TestScaleHelper.setSystemProperty("java.awt.headless", "false");
  }

  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void before() {
    TestScaleHelper.setSystemProperty("ide.browser.jcef.jsQueryPoolSize", "1");
  }

  @After
  public void after() {
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test1() {
    TestScaleHelper.assumeStandalone();

    JBCefJSQueryPoolSizePropTest.test(b -> null);
  }
}
