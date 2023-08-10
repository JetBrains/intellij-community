// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class UseDPIAwareInsetsFixTest extends UseDPIAwareInsetsFixTestBase {

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  // AWT Insets conversion tests:

  public void testAwtInsetsWithFourZeros() {
    doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  public void testAwtInsetsWithFourTheSameValues() {
    doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(1, 1, 1, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1);")
    );
  }

  public void testAwtInsetsWithFirstThirdAndSecondFourthValuesTheSame() {
    doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(1, 2, 1, 2)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1, 2);")
    );
  }

  public void testAwtInsetsWithThreeZerosAndDifferentFirstParam() {
    doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(1, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsTop(1);")
    );
  }

  public void testAwtInsetsWithThreeZerosAndDifferentSecondParam() {
    doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 1, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsLeft(1);")
    );
  }

  public void testAwtInsetsWithThreeZerosAndDifferentThirdParam() {
    doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 0, 1, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsBottom(1);")
    );
  }

  public void testAwtInsetsWithThreeZerosAndDifferentFourthParam() {
    doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 0, 0, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsRight(1);")
    );
  }

  // Simplifying tests:

  public void testJBUIInsetsEmptyWithZero() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  public void testJBUIInsetsEmptyWithTwoZeros() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  public void testJBUIInsetsEmptyWithTopAndBottomEqualToLeftAndRight() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1);")
    );
  }

  public void testJBUIInsetsEmptyWithFourZeros() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  public void testJBUIInsetsEmptyWithFourTheSameValues() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 1, 1, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1);")
    );
  }

  public void testJBUIInsetsEmptyWithFirstThirdAndSecondFourthValuesTheSame() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 2, 1, 2)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1, 2);")
    );
  }

  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentFirstParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsTop(1);")
    );
  }

  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentSecondParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 1, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsLeft(1);")
    );
  }

  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentThirdParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0, 1, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsBottom(1);")
    );
  }

  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentFourthParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0, 0, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsRight(1);")
    );
  }

  public void testJBUIInsetsEmptyWithFourZerosAndStaticImportForInsets() {
    doTest(
      SIMPLIFY_FIX_NAME,
      java("""
             import java.awt.Insets;
             import static com.intellij.util.ui.JBUI.insets;

             class TestClass {
               void any() {
                 Insets myInsets = <warning descr="Insets creation can be simplified">in<caret>sets(0, 0, 0, 0)</warning>;
               }
             }
             """),
      java("""
             import com.intellij.util.ui.JBUI;

             import java.awt.Insets;
             import static com.intellij.util.ui.JBUI.insets;

             class TestClass {
               void any() {
                 Insets myInsets = JBUI.emptyInsets();
               }
             }
             """)
    );
  }

  private static String awtInsets(String code) {
    return """
      import java.awt.Insets;

      class TestClass {
        void any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String jbuiInsets(String code) {
    return """
      import com.intellij.util.ui.JBUI;

      import java.awt.Insets;

      class TestClass {
        void any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String java(@Language("JAVA") String code) {
    return code;
  }
}
