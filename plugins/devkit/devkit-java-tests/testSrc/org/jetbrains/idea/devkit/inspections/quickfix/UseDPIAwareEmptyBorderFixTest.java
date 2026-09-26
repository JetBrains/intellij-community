// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class UseDPIAwareEmptyBorderFixTest extends UseDPIAwareEmptyBorderFixTestBase {

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  // Swing EmptyBorder conversion tests:

  public void testSwingEmptyBorderWithFourZeros() {
    doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("Border myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">new Empty<caret>Border(0, 0, 0, 0)</warning>;"),
      jbuiBorder("Border myBorder = JBUI.Borders.empty();")
    );
  }

  public void testSwingEmptyBorderWithFourTheSameValues() {
    doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("Border myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">new Empty<caret>Border(1, 1, 1, 1)</warning>;"),
      jbuiBorder("Border myBorder = JBUI.Borders.empty(1);")
    );
  }

  public void testSwingEmptyBorderWithFirstThirdAndSecondFourthValuesTheSame() {
    doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("Border myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">new Empty<caret>Border(1, 2, 1, 2)</warning>;"),
      jbuiBorder("Border myBorder = JBUI.Borders.empty(1, 2);")
    );
  }

  public void testSwingEmptyBorderWithThreeZerosAndDifferentFirstParam() {
    doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("Border myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">new Empty<caret>Border(1, 0, 0, 0)</warning>;"),
      jbuiBorder("Border myBorder = JBUI.Borders.emptyTop(1);")
    );
  }

  public void testSwingEmptyBorderWithThreeZerosAndDifferentSecondParam() {
    doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("Border myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">new Empty<caret>Border(0, 1, 0, 0)</warning>;"),
      jbuiBorder("Border myBorder = JBUI.Borders.emptyLeft(1);")
    );
  }

  public void testSwingEmptyBorderWithThreeZerosAndDifferentThirdParam() {
    doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("Border myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">new Empty<caret>Border(0, 0, 1, 0)</warning>;"),
      jbuiBorder("Border myBorder = JBUI.Borders.emptyBottom(1);")
    );
  }

  public void testSwingEmptyBorderWithThreeZerosAndDifferentFourthParam() {
    doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("Border myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">new Empty<caret>Border(0, 0, 0, 1)</warning>;"),
      jbuiBorder("Border myBorder = JBUI.Borders.emptyRight(1);")
    );
  }

  // Simplifying tests:

  public void testJBUIBordersEmptyWithZero() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(0)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.empty();")
    );
  }

  public void testJBUIBordersEmptyWithTwoZeros() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(0, 0)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.empty();")
    );
  }

  public void testJBUIBordersEmptyWithTopAndBottomEqualToLeftAndRight() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(1, 1)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.empty(1);")
    );
  }

  public void testJBUIBordersEmptyWithFourZeros() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(0, 0, 0, 0)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.empty();")
    );
  }

  public void testJBUIBordersEmptyWithFourTheSameValues() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(1, 1, 1, 1)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.empty(1);")
    );
  }

  public void testJBUIBordersEmptyWithFirstThirdAndSecondFourthValuesTheSame() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(1, 2, 1, 2)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.empty(1, 2);")
    );
  }

  public void testJBUIBordersEmptyWithThreeZerosAndDifferentFirstParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(1, 0, 0, 0)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.emptyTop(1);")
    );
  }

  public void testJBUIBordersEmptyWithThreeZerosAndDifferentSecondParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(0, 1, 0, 0)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.emptyLeft(1);")
    );
  }

  public void testJBUIBordersEmptyWithThreeZerosAndDifferentThirdParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(0, 0, 1, 0)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.emptyBottom(1);")
    );
  }

  public void testJBUIBordersEmptyWithThreeZerosAndDifferentFourthParam() {
    doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "Border myBorder = <warning descr=\"Empty border creation can be simplified\">JBUI.Border<caret>s.empty(0, 0, 0, 1)</warning>;"),
      jbuiEmptyBorder("Border myBorder = JBUI.Borders.emptyRight(1);")
    );
  }

  public void testJBUIBordersEmptyWithFourZerosAndStaticImportForBorders() {
    doTest(
      SIMPLIFY_FIX_NAME,
      java("""
             import javax.swing.border.Border;
             import static com.intellij.util.ui.JBUI.Borders;

             class TestClass {
               void any() {
                 Border myBorder = <warning descr="Empty border creation can be simplified">Border<caret>s.empty(0, 0, 0, 0)</warning>;
               }
             }
             """),
      java("""
             import javax.swing.border.Border;
             import static com.intellij.util.ui.JBUI.Borders;

             class TestClass {
               void any() {
                 Border myBorder = Borders.empty();
               }
             }
             """)
    );
  }

  public void testJBUIBordersEmptyWithFourZerosAndStaticImportForBordersEmpty() {
    doTest(
      SIMPLIFY_FIX_NAME,
      java("""
             import javax.swing.border.Border;
             import static com.intellij.util.ui.JBUI.Borders.empty;

             class TestClass {
               void any() {
                 Border myBorder = <warning descr="Empty border creation can be simplified">em<caret>pty(0, 0, 0, 0)</warning>;
               }
             }
             """),
      java("""
             import javax.swing.border.Border;
             import static com.intellij.util.ui.JBUI.Borders.empty;

             class TestClass {
               void any() {
                 Border myBorder = empty();
               }
             }
             """)
    );
  }

  private static String swingBorder(String code) {
    return """
      import javax.swing.border.Border;
      import javax.swing.border.EmptyBorder;

      class TestClass {
        void any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String jbuiBorder(String code) {
    return """
      import com.intellij.util.ui.JBUI;

      import javax.swing.border.Border;
      import javax.swing.border.EmptyBorder;

      class TestClass {
        void any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String jbuiEmptyBorder(String code) {
    return """
      import com.intellij.util.ui.JBUI;
      import javax.swing.border.Border;

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
