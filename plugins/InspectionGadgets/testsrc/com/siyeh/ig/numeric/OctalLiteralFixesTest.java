/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ig.numeric;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;

public class OctalLiteralFixesTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new OctalLiteralInspection());
  }

  public void testConvertOctalToDecimal1() {
    doTest("ConvertToDecimal1", InspectionGadgetsBundle.message("convert.octal.literal.to.decimal.literal.quickfix"));
  }

  public void testConvertOctalToDecimal2() {
    doTest("ConvertToDecimal2", InspectionGadgetsBundle.message("convert.octal.literal.to.decimal.literal.quickfix"));
  }

  public void testRemoveLeadingZero() {
    doTest("RemoveLeadingZero", InspectionGadgetsBundle.message("remove.leading.zero.to.make.decimal.quickfix"));
  }

  @Override
  protected String getRelativePath() {
    return "numeric/octal";
  }
}
