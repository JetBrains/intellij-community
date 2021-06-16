// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class BigDecimalLegacyMethodInspectionTest extends LightJavaInspectionTestCase {
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
    // noinspection BigDecimalLegacyMethod, deprecation
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
