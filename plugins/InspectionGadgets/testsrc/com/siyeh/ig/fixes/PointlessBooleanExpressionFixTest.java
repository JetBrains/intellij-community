/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.siyeh.ig.fixes;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.PointlessBooleanExpressionInspection;

public class PointlessBooleanExpressionFixTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new PointlessBooleanExpressionInspection());
    myRelativePath = "pointlessboolean";
    myDefaultHint = InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
  }

  public void testNegation() { doTest(); }
  public void testPolyadic() { doTest(); }
  public void testBoxed() { doTest(); }
  public void testSideEffects() {
    doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect"));
  }
  public void testSideEffectsField() {
    doTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect"));
  }
}
