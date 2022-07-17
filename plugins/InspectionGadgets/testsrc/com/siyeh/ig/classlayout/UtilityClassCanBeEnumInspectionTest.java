// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UtilityClassCanBeEnumInspectionTest extends LightJavaInspectionTestCase {

  public void testUtilityClassCanBeEnum() {
    doTest();
  }

  public void testQuickfix() {
    doTest("final class /*Utility class 'Util' can be 'enum'*//*_*/Util/**/ {\n" +
           "  public static void driveCar() {}\n" +
           "}");
    checkQuickFix("Convert to 'enum'",
                  "enum Util {\n" +
                  "    ;\n" +
                  "\n" +
                  "    public static void driveCar() {}\n" +
                  "}");
  }

  public void testUtilityClassInstantiation() {
    doTest("class SmartStepClass {\n" +
           "  public static final int a = 1;\n" +
           "  public static final String b = String.valueOf(2);\n" +
           "\n" +
           "  public static void main(String[] args) {\n" +
           "    new SmartStepClass();\n" +
           "  }\n" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UtilityClassCanBeEnumInspection();
  }
}