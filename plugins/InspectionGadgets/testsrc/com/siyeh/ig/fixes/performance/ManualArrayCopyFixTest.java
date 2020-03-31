package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.ManualArrayCopyInspection;

public class ManualArrayCopyFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ManualArrayCopyInspection());
    myRelativePath = "performance/replace_with_system_arraycopy";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.with.x", "System.arraycopy()");
  }

  public void testSimple() { doTest(); }
  public void testDecrement() { doTest(); }
  public void testLengthSmallerThanOffset() { doTest(); }
}