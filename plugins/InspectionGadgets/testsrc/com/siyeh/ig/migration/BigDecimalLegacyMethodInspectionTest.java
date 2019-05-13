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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class BigDecimalLegacyMethodInspectionTest extends LightInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new BigDecimalLegacyMethodInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.math;" +
      "public class BigDecimal {" +
      "  public static final int ROUND_HALF_DOWN = 5;" +
      "  public BigDecimal divide(BigDecimal divisor, int roundingMode) { return null; }" +
      "  public BigDecimal divide(BigDecimal divisor, int scale, int roundingMode) { return null; }" +
      "  public BigDecimal divide(BigDecimal divisor, int scale, RoundingMode roundingMode) { return null; }" +
      "  public BigDecimal setScale(int newScale, int roundingMode) { return null; }" +
      "  public BigDecimal setScale(int newScale, RoundingMode roundingMode) { return null; }" +
      "}",
      "package java.math;" +
      "public enum RoundingMode {" +
      "  UNNECESSARY, CEILING" +
      "}"
    };
  }

  public void testSimple() {
    doTest(
      "import java.math.*;" +
      "class X {" +
      "  void m(BigDecimal value) {" +
      "    value./*Call to 'BigDecimal.divide()' can use 'RoundingMode' enum constant*/divide/**/(value, BigDecimal.ROUND_HALF_DOWN);" +
      "    value./*Call to 'BigDecimal.divide()' can use 'RoundingMode' enum constant*/divide/**/(value, 2, 7);" +
      "    value.divide(value, 2, RoundingMode.UNNECESSARY);" +
      "    value./*Call to 'BigDecimal.setScale()' can use 'RoundingMode' enum constant*/setScale/**/(2, 4);" +
      "    value.setScale(2, RoundingMode.CEILING);" +
      "  }" +
      "}");
  }
}
