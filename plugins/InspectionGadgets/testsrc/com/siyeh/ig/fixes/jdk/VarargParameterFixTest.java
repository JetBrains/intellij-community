package com.siyeh.ig.fixes.jdk;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.jdk.VarargParameterInspection;

public class VarargParameterFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new VarargParameterInspection());
    myRelativePath = "jdk/vararg_parameter";
    myDefaultHint = InspectionGadgetsBundle.message("variable.argument.method.quickfix");
  }

  public void testGenericType() { doTest(); }
}