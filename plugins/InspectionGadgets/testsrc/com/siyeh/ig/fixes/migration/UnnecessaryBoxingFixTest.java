/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public void testLiteral1() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                     "Long l = new/**/ Long(1);",
                     "Long l = 1L;");
  }

  public void testLiteral2() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new/**/ Float(1);",
                 "Float l = 1f;");
  }

  public void testLiteral3() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new/**/ Float(1.0);",
                 "Float l = 1.0f;");
  }

  public void testLiteral4() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new/**/ Float(1d);",
                 "Float l = (float) 1d;");
  }

  public void testLiteral5() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Double l = new/**/ Double(1);",
                 "Double l = 1d;");
  }

  public void testCast() {
    doFixTest();
  }

  private void doFixTest() {
    doTest(getTestName(false), InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"));
  }

  @Override
  protected String getRelativePath() {
    return "migration/unnecessary_boxing";
  }
}