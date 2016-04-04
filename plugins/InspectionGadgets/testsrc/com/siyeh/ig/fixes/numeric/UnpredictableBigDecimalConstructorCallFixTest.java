/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.numeric;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.numeric.UnpredictableBigDecimalConstructorCallInspection;

/**
 * @author Bas Leijdekkers
 */
public class UnpredictableBigDecimalConstructorCallFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final UnpredictableBigDecimalConstructorCallInspection inspection = new UnpredictableBigDecimalConstructorCallInspection();
    inspection.ignoreReferences = false;
    myFixture.enableInspections(inspection);
    myFixture.addClass(
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal(double d) {}" +
      "}"
    );
  }

  @Override
  protected String getRelativePath() {
    return "numeric/unpredictable_big_decimal";
  }

  public void testFactory() {
    doTest(InspectionGadgetsBundle.message("unpredictable.big.decimal.constructor.call.quickfix", "BigDecimal.valueOf(val)"));
  }

  public void testConstructor() {
    doTest(InspectionGadgetsBundle.message("unpredictable.big.decimal.constructor.call.quickfix", "new BigDecimal(\"0.1\")"));
  }

  public void testLiteral() {
    doTest(InspectionGadgetsBundle.message("unpredictable.big.decimal.constructor.call.quickfix", "BigDecimal.valueOf(2d)"));
  }
}
