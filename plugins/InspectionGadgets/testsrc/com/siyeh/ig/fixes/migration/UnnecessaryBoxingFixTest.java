package com.siyeh.ig.fixes.migration;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.UnnecessaryBoxingInspection;

public class UnnecessaryBoxingFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryBoxingInspection());
  }

  public void testCast() {
    doFixTest();
  }

  private void doFixTest() {
    doTest(getTestName(true), InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"));
  }

  @Override
  protected String getRelativePath() {
    return "migration/unnecessary_boxing";
  }
}