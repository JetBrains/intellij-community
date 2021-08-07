package com.siyeh.ig.fixes.style;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnnecessaryCallToStringValueOfInspection;

public class UnnecessaryCallToStringValueOfFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryCallToStringValueOfInspection());
    myRelativePath = "style/unnecessary_valueof";
    myDefaultHint = "Fix all 'Unnecessary conversion to String' problems in file";
  }

  public void testUnnecessaryCall() { doTest(); }
}
