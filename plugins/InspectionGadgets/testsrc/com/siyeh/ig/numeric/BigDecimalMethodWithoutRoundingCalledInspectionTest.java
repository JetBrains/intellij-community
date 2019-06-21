// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class BigDecimalMethodWithoutRoundingCalledInspectionTest extends LightJavaInspectionTestCase {

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

  public void testNoWarnOnOtherMethod() {
    doTest("import java.math.BigDecimal;\n" +
           "import java.math.RoundingMode;\n" +
           "class B {\n" +
           "    public BigDecimal scaleValue(BigDecimal v) {\n" +
           "        return setScale(v);\n" +
           "    }\n" +
           "\n" +
           "    public static BigDecimal setScale(BigDecimal v) {\n" +
           "        return v != null ? v.setScale(6, RoundingMode.HALF_EVEN) : null;\n" +
           "    }\n" +
           "}");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}