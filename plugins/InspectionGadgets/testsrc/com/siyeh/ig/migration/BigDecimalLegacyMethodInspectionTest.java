// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class BigDecimalLegacyMethodInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new BigDecimalLegacyMethodInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  public void testSimple() {
    // noinspection BigDecimalLegacyMethod, deprecation, ResultOfMethodCallIgnored
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

  public void testNoWarn() {
    // noinspection BigDecimalLegacyMethod, deprecation
    doTest("import java.math.BigDecimal;\n" +
           "import java.math.RoundingMode;\n" +
           "class SetScaleDetectionIssueTest {\n" +
           "    void something() {\n" +
           "        System.out.println(BigDecimal.valueOf(42)./*Call to 'BigDecimal.setScale()' can use 'RoundingMode' enum constant*/setScale/**/(2, 1).toString());\n" +
           "        System.out.println(setScale(BigDecimal.valueOf(42), 2).toString());\n" +
           "        System.out.println(setScale(2, BigDecimal.valueOf(42)).toString());\n" +
           "        System.out.println(setScale(BigDecimal.valueOf(42), 2, \"blah-blah\").toString());\n" +
           "        System.out.println(setScale(\"decr\", BigDecimal.valueOf(42), 2).toString());\n" +
           "        System.out.println(setScaleOtherWay(BigDecimal.valueOf(42), 2).toString());\n" +
           "        System.out.println(BigDecimal.valueOf(42)./*Call to 'BigDecimal.divide()' can use 'RoundingMode' enum constant*/divide/**/(BigDecimal.valueOf(2), 1).toString());\n" +
           "        System.out.println(divide(BigDecimal.valueOf(42), 2).toString());\n" +
           "        System.out.println(divide(2, BigDecimal.valueOf(42)).toString());\n" +
           "        System.out.println(divideOtherWay(BigDecimal.valueOf(42), 2).toString());\n" +
           "    }\n" +
           "    // Inspection found\n" +
           "    static BigDecimal setScale(final BigDecimal value, final int scale) {\n" +
           "        return value.setScale(scale, RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "    // Inspection found\n" +
           "    static BigDecimal setScale(final String descr, final BigDecimal value, final int scale) {\n" +
           "        return value.setScale(scale, RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "    // Inspection not found\n" +
           "    static BigDecimal setScale(final BigDecimal value, final int scale, final String descr) {\n" +
           "        return value.setScale(scale, RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "    // Inspection not found\n" +
           "    static BigDecimal setScale(final int scale, final BigDecimal value) {\n" +
           "        return value.setScale(scale, RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "    // Inspection not found\n" +
           "    static BigDecimal setScaleOtherWay(final BigDecimal value, final int scale) {\n" +
           "        return value.setScale(scale, RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "    // Inspection found\n" +
           "    static BigDecimal divide(final BigDecimal value, final int divisor) {\n" +
           "        return value.divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "    // Inspection not found\n" +
           "    static BigDecimal divide(final int divisor, final BigDecimal value) {\n" +
           "        return value.divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "    // Inspection not found\n" +
           "    static BigDecimal divideOtherWay(final BigDecimal value, final int divisor) {\n" +
           "        return value.divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP);\n" +
           "    }\n" +
           "}");
  }
}
