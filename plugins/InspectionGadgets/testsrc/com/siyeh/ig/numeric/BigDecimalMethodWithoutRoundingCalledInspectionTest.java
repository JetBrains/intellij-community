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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class BigDecimalMethodWithoutRoundingCalledInspectionTest extends LightInspectionTestCase {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new BigDecimalMethodWithoutRoundingCalledInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal setScale(int newScale) {}" +
      "  public BigDecimal setScale(int newScale, int roundingMode) {}" +
      "  public BigDecimal divide(BigDecimal divisor) {}" +
      "  public BigDecimal divide(BigDecimal divisor, int roundingMode) {}" +
      "}"
    };
  }

  public void testSetScale() {
    doTest("import java.math.BigDecimal;" +
           "class X {" +
           "  void foo(BigDecimal value) {" +
           "    value./*'BigDecimal.setScale()' called without a rounding mode argument*/setScale/**/(2);" +
           "    value.setScale(2, 1);" +
           "  }" +
           "}");
  }

  public void testDivide() {
    doTest("import java.math.BigDecimal;" +
           "class X {" +
           "  void foo(BigDecimal value) {" +
           "    value./*'BigDecimal.divide()' called without a rounding mode argument*/divide/**/(value);" +
           "    value.divide(value, 1);" +
           "  }" +
           "}");
  }
}