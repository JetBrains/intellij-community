// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.quickfix;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareEmptyBorderFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareEmptyBorderFixture.CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME;
import static org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareEmptyBorderFixture.SIMPLIFY_FIX_NAME;
import static org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareEmptyBorderFixtureKt.useDPIAwareEmptyBorderFixture;

@TestApplication
public class KtUseDPIAwareEmptyBorderFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<UseDPIAwareEmptyBorderFixture> emptyBorder = useDPIAwareEmptyBorderFixture(projectFixture, "kt");

  // Swing EmptyBorder conversion tests:

  @Test
  public void testSwingEmptyBorderWithFourZeros() {
    emptyBorder.get().doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("val myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">Empty<caret>Border(0, 0, 0, 0)</warning>"),
      jbuiBorder("val myBorder = JBUI.Borders.empty()")
    );
  }

  @Test
  public void testSwingEmptyBorderWithFourTheSameValues() {
    emptyBorder.get().doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("val myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">Empty<caret>Border(1, 1, 1, 1)</warning>"),
      jbuiBorder("val myBorder = JBUI.Borders.empty(1)")
    );
  }

  @Test
  public void testSwingEmptyBorderWithFirstThirdAndSecondFourthValuesTheSame() {
    emptyBorder.get().doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("val myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">Empty<caret>Border(1, 2, 1, 2)</warning>"),
      jbuiBorder("val myBorder = JBUI.Borders.empty(1, 2)")
    );
  }

  @Test
  public void testSwingEmptyBorderWithThreeZerosAndDifferentFirstParam() {
    emptyBorder.get().doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("val myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">Empty<caret>Border(1, 0, 0, 0)</warning>"),
      jbuiBorder("val myBorder = JBUI.Borders.emptyTop(1)")
    );
  }

  @Test
  public void testSwingEmptyBorderWithThreeZerosAndDifferentSecondParam() {
    emptyBorder.get().doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("val myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">Empty<caret>Border(0, 1, 0, 0)</warning>"),
      jbuiBorder("val myBorder = JBUI.Borders.emptyLeft(1)")
    );
  }

  @Test
  public void testSwingEmptyBorderWithThreeZerosAndDifferentThirdParam() {
    emptyBorder.get().doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("val myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">Empty<caret>Border(0, 0, 1, 0)</warning>"),
      jbuiBorder("val myBorder = JBUI.Borders.emptyBottom(1)")
    );
  }

  @Test
  public void testSwingEmptyBorderWithThreeZerosAndDifferentFourthParam() {
    emptyBorder.get().doTest(
      CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME,
      swingBorder("val myBorder = <warning descr=\"'EmptyBorder' is not DPI-aware\">Empty<caret>Border(0, 0, 0, 1)</warning>"),
      jbuiBorder("val myBorder = JBUI.Borders.emptyRight(1)")
    );
  }

  // Simplifying tests:

  @Test
  public void testJBUIBordersEmptyWithZero() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder("val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">e<caret>mpty(0)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.empty()")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithTwoZeros() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">em<caret>pty(0, 0)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.empty()")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithTopAndBottomEqualToLeftAndRight() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">e<caret>mpty(1, 1)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.empty(1)")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithFourZeros() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">em<caret>pty(0, 0, 0, 0)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.empty()")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithFourTheSameValues() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">em<caret>pty(1, 1, 1, 1)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.empty(1)")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithFirstThirdAndSecondFourthValuesTheSame() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">emp<caret>ty(1, 2, 1, 2)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.empty(1, 2)")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithThreeZerosAndDifferentFirstParam() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">emp<caret>ty(1, 0, 0, 0)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.emptyTop(1)")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithThreeZerosAndDifferentSecondParam() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">em<caret>pty(0, 1, 0, 0)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.emptyLeft(1)")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithThreeZerosAndDifferentThirdParam() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">emp<caret>ty(0, 0, 1, 0)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.emptyBottom(1)")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithThreeZerosAndDifferentFourthParam() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiEmptyBorder(
        "val myBorder = JBUI.Borders.<warning descr=\"Empty border creation can be simplified\">em<caret>pty(0, 0, 0, 1)</warning>"),
      jbuiEmptyBorder("val myBorder = JBUI.Borders.emptyRight(1)")
    );
  }

  @Test
  public void testJBUIBordersEmptyWithFourZerosAndStaticImportForBorders() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      kotlin("""
               import javax.swing.border.Border
               import com.intellij.util.ui.JBUI.Borders
               
               @Suppress("UNUSED_VARIABLE")
               class TestClass {
                 fun any() {
                   val myBorder = Borders.<warning descr="Empty border creation can be simplified">em<caret>pty(0, 0, 0, 0)</warning>;
                 }
               }
               """),
      kotlin("""
               import javax.swing.border.Border
               import com.intellij.util.ui.JBUI.Borders
               
               @Suppress("UNUSED_VARIABLE")
               class TestClass {
                 fun any() {
                   val myBorder = Borders.empty();
                 }
               }
               """)
    );
  }

  @Test
  public void testJBUIBordersEmptyWithFourZerosAndStaticImportForBordersEmpty() {
    emptyBorder.get().doTest(
      SIMPLIFY_FIX_NAME,
      kotlin("""
               import javax.swing.border.Border
               import com.intellij.util.ui.JBUI.Borders.empty
               
               @Suppress("UNUSED_VARIABLE")
               class TestClass {
                 fun any() {
                   val myBorder = <warning descr="Empty border creation can be simplified">em<caret>pty(0, 0, 0, 0)</warning>;
                 }
               }
               """),
      kotlin("""
               import javax.swing.border.Border
               import com.intellij.util.ui.JBUI.Borders.empty
               
               @Suppress("UNUSED_VARIABLE")
               class TestClass {
                 fun any() {
                   val myBorder = empty();
                 }
               }
               """)
    );
  }

  private static String swingBorder(String code) {
    return """
      import javax.swing.border.Border
      import javax.swing.border.EmptyBorder
      
      @Suppress("UNUSED_VARIABLE")
      class TestClass {
        fun any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String jbuiBorder(String code) {
    return """
      import com.intellij.util.ui.JBUI
      import javax.swing.border.Border
      import javax.swing.border.EmptyBorder
      
      @Suppress("UNUSED_VARIABLE")
      class TestClass {
        fun any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String jbuiEmptyBorder(String code) {
    return """
      import com.intellij.util.ui.JBUI
      import javax.swing.border.Border
      
      @Suppress("UNUSED_VARIABLE")
      class TestClass {
        fun any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String kotlin(@Language("kotlin") String code) {
    return code;
  }
}
