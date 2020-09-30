// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.UnnecessaryBoxingInspection;

public class UnnecessaryBoxingFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryBoxingInspection());
  }

  public void testLiteral1() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                     "Long l = new/**/ Long(1);",
                     "Long l = 1L;");
  }

  public void testLiteral2() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new/**/ Float(1);",
                 "Float l = 1F;");
  }

  public void testLiteral3() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new/**/ Float(1.0);",
                 "Float l = 1.0F;");
  }

  public void testLiteral4() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new/**/ Float(1d);",
                 "Float l = 1F;");
  }

  public void testLiteral5() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Double l = new/**/ Double(1);",
                 "Double l = 1.0;");
  }

  public void testBooleanLiteral() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "final Boolean aBoolean = Boolean.valueOf(/**/true);",
                 "final Boolean aBoolean = Boolean.TRUE;");
  }

  public void testStringConcatenation() {
    doExpressionTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                     "\"a\" + Long/**/.valueOf(1L + 2L) + \"b\"",
                     "\"a\" + (1L + 2L) + \"b\"");
  }

  public void testStringConcatenation2() {
    doExpressionTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                     "\"a\" + Long/**/.valueOf(1L - 2L) + \"b\"",
                     "\"a\" + (1L - 2L) + \"b\"");
  }

  public void testHex() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "float f = Float./**/valueOf(0x123);",
                 "float f = (float) 0x123;");
  }

  public void testHexDouble() {
    //noinspection RedundantCast
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "double f = Double./**/valueOf(0x123);",
                 "double f = (double) 0x123;");
  }

  @SuppressWarnings("OctalInteger")
  public void testOctal() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "float f = Float.valueOf/**/(0123);",
                 "float f = (float) 0123;");
  }

  @SuppressWarnings("OctalInteger")
  public void testOctalDouble() {
    //noinspection RedundantCast
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "double f = Double.valueOf/**/(0123);",
                 "double f = (double) 0123;");
  }

  public void testCast() {
    doFixTest();
  }

  public void testParseInt() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "parseInt"));
  }

  public void testStaticImport() {
    doTest(CommonQuickFixBundle.message("fix.replace.with.x", "parseInt"));
  }

  public void testShadowImport() {
    assertQuickfixNotAvailable("Fix all 'Unnecessary boxing' problems in file");
  }

  private void doFixTest() {
    doTest(getTestName(false), InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"));
  }

  @Override
  protected String getRelativePath() {
    return "migration/unnecessary_boxing";
  }
}