/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnnecessaryConstantArrayCreationExpressionInspection;

public class UnnecessaryConstantArrayCreationExpressionFixTest extends IGQuickFixesTestCase {

  public void testPrimitive() { doTest("int[]"); }
  public void testTwoDimension() { doTest("Map[][]"); }
  public void testInitializerWithoutNew() { assertQuickfixNotAvailable(); }

  @Override
  protected void doTest(String hint) {
    super.doTest(InspectionGadgetsBundle.message("unnecessary.constant.array.creation.expression.quickfix", hint));
  }

  @Override
  protected void assertQuickfixNotAvailable() {
    String message = InspectionGadgetsBundle.message("unnecessary.constant.array.creation.expression.quickfix", "@");
    super.assertQuickfixNotAvailable(message.substring(0, message.indexOf('@')));
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnnecessaryConstantArrayCreationExpressionInspection());
    myRelativePath = "style/unnecessary_constant_array_creation_expression";
  }
}
