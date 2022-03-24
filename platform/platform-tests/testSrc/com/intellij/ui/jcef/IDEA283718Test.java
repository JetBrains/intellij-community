// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.ui.scale.TestScaleHelper;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests https://youtrack.jetbrains.com/issue/IDEA-283718
 * 1) JCEFHtmlPanel should not additionally chain the new browser instance for disposal.
 * 2) JCEFHtmlPanel should be auto-disposed after its client.
 *
 * @author tav
 */
public class IDEA283718Test {
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
    //
    // Try external JBCefClient.
    //
    doTest(JBCefApp.getInstance().createClient());

    //
    // Try default JBCefClient.
    //
    doTest(null);
  }

  private static void doTest(@Nullable JBCefClient client) {
    JBCefBrowserBase browser = null;
    try {
      browser = new JCEFHtmlPanel(client, "about:blank");
    } catch (RuntimeException ex) {
      TestCase.fail("Exception occurred: " + ex.getMessage());
      ex.printStackTrace();
    }
    if (client == null) client = browser.getJBCefClient();
    TestCase.assertFalse(browser.isDisposed());
    Disposer.dispose(client);
    TestCase.assertTrue(browser.isDisposed());
  }
}
