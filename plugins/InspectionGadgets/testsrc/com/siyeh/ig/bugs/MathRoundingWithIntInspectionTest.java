// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class MathRoundingWithIntInspectionTest extends LightJavaInspectionTestCase {

  public void testWarningMathRoundingWithInt() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new MathRoundingWithIntArgumentInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
      package java.lang;
      public final class StrictMath{
        public static long round(float a) {
            return Math.round(a);
        }
        public static double floor(double a) {
            return Math.floor(a);
        }
        public static double ceil(double a) {
            return Math.ceil(a);
        }
        public static double rint(double a) {
            return Math.rint(a);
        }
        public static double sin(double a) {
            return Math.sin(a);
        }
      }
      """
    };
  }
}
