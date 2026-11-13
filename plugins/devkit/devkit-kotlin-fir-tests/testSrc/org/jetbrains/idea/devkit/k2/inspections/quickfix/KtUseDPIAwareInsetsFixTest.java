// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.quickfix;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareInsetsFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareInsetsFixture.CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME;
import static org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareInsetsFixture.SIMPLIFY_FIX_NAME;
import static org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareInsetsFixtureKt.useDPIAwareInsetsFixture;

@TestApplication
public class KtUseDPIAwareInsetsFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<UseDPIAwareInsetsFixture> insets = useDPIAwareInsetsFixture(projectFixture, "kt");

  // AWT Insets conversion tests:

  @Test
  public void testAwtInsetsWithFourZeros() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("val myInsets = <warning descr=\"'Insets' is not DPI-aware\">In<caret>sets(0, 0, 0, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.emptyInsets()")
    );
  }

  @Test
  public void testAwtInsetsWithFourTheSameValues() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("val myInsets = <warning descr=\"'Insets' is not DPI-aware\">In<caret>sets(1, 1, 1, 1)</warning>"),
      jbuiInsets("val myInsets = JBUI.insets(1)")
    );
  }

  @Test
  public void testAwtInsetsWithFirstThirdAndSecondFourthValuesTheSame() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("val myInsets = <warning descr=\"'Insets' is not DPI-aware\">In<caret>sets(1, 2, 1, 2)</warning>"),
      jbuiInsets("val myInsets = JBUI.insets(1, 2)")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentFirstParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("val myInsets = <warning descr=\"'Insets' is not DPI-aware\">In<caret>sets(1, 0, 0, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsTop(1)")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentSecondParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("val myInsets = <warning descr=\"'Insets' is not DPI-aware\">In<caret>sets(0, 1, 0, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsLeft(1)")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentThirdParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("val myInsets = <warning descr=\"'Insets' is not DPI-aware\">In<caret>sets(0, 0, 1, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsBottom(1)")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentFourthParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("val myInsets = <warning descr=\"'Insets' is not DPI-aware\">In<caret>sets(0, 0, 0, 1)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsRight(1)")
    );
  }

  // Simplifying tests:

  @Test
  public void testJBUIInsetsEmptyWithZero() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(0)</warning>"),
      jbuiInsets("val myInsets = JBUI.emptyInsets()")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithTwoZeros() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(0, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.emptyInsets()")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithTopAndBottomEqualToLeftAndRight() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(1, 1)</warning>"),
      jbuiInsets("val myInsets = JBUI.insets(1)")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFourZeros() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(0, 0, 0, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.emptyInsets()")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFourTheSameValues() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(1, 1, 1, 1)</warning>"),
      jbuiInsets("val myInsets = JBUI.insets(1)")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFirstThirdAndSecondFourthValuesTheSame() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(1, 2, 1, 2)</warning>"),
      jbuiInsets("val myInsets = JBUI.insets(1, 2)")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentFirstParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(1, 0, 0, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsTop(1)")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentSecondParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(0, 1, 0, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsLeft(1)")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentThirdParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(0, 0, 1, 0)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsBottom(1)")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentFourthParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("val myInsets = JBUI.<warning descr=\"Insets creation can be simplified\">in<caret>sets(0, 0, 0, 1)</warning>"),
      jbuiInsets("val myInsets = JBUI.insetsRight(1)")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFourZerosAndStaticImportForInsets() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      kotlin("""
               import com.intellij.util.ui.JBUI.insets
               import java.awt.Insets
               
               @Suppress("UNUSED_VARIABLE")
               class TestClass {
                 fun any() {
                   val myInsets = <warning descr="Insets creation can be simplified">in<caret>sets(0, 0, 0, 0)</warning>;
                 }
               }
               """),
      kotlin("""
               import com.intellij.util.ui.JBUI
               import com.intellij.util.ui.JBUI.insets
               import java.awt.Insets
               
               @Suppress("UNUSED_VARIABLE")
               class TestClass {
                 fun any() {
                   val myInsets = JBUI.emptyInsets();
                 }
               }
               """)
    );
  }

  private static String awtInsets(String code) {
    return """
      import java.awt.Insets
      
      @Suppress("UNUSED_VARIABLE")
      class TestClass {
        fun any() {
          <code>
        }
      }
      """.replace("<code>", code);
  }

  private static String jbuiInsets(String code) {
    return """
      import com.intellij.util.ui.JBUI
      import java.awt.Insets
      
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
