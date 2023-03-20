// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"BigDecimalMethodWithoutRoundingCalled", "BigDecimalLegacyMethod"})
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
    // noinspection BigDecimalLegacyMethod
    doTest("import java.math.BigDecimal;" +
           "class X {" +
           "  static void foo(BigDecimal value) {" +
           "    value = value./*'BigDecimal.setScale()' called without a rounding mode argument*/setScale/**/(2);" +
           "    System.out.println(value.setScale(2, 1));" +
           "  }" +
           "}");
  }

  public void testDivide() {
    // noinspection BigDecimalLegacyMethod
    doTest("import java.math.BigDecimal;" +
           "class X {" +
           "  static void foo(BigDecimal value) {" +
           "    value = value./*'BigDecimal.divide()' called without a rounding mode argument*/divide/**/(value);" +
           "    System.out.println(value.divide(value, 1));" +
           "  }" +
           "}");
  }

  public void testNoWarnOnOtherMethod() {
    doTest("""
             import java.math.BigDecimal;
             import java.math.RoundingMode;
             class B {
                 public static BigDecimal scaleValue(BigDecimal v) {
                     return setScale(v);
                 }

                 public static BigDecimal setScale(BigDecimal v) {
                     return v != null ? v.setScale(6, RoundingMode.HALF_EVEN) : null;
                 }
             }""");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}