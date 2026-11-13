// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

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
public class UseDPIAwareInsetsFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<UseDPIAwareInsetsFixture> insets = useDPIAwareInsetsFixture(projectFixture, "java");

  // AWT Insets conversion tests:

  @Test
  public void testAwtInsetsWithFourZeros() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  @Test
  public void testAwtInsetsWithFourTheSameValues() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(1, 1, 1, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1);")
    );
  }

  @Test
  public void testAwtInsetsWithFirstThirdAndSecondFourthValuesTheSame() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(1, 2, 1, 2)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1, 2);")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentFirstParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(1, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsTop(1);")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentSecondParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 1, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsLeft(1);")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentThirdParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 0, 1, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsBottom(1);")
    );
  }

  @Test
  public void testAwtInsetsWithThreeZerosAndDifferentFourthParam() {
    insets.get().doTest(
      CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME,
      awtInsets("Insets myInsets = <warning descr=\"'Insets' is not DPI-aware\">new In<caret>sets(0, 0, 0, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsRight(1);")
    );
  }

  // Simplifying tests:

  @Test
  public void testJBUIInsetsEmptyWithZero() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithTwoZeros() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithTopAndBottomEqualToLeftAndRight() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1);")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFourZeros() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.emptyInsets();")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFourTheSameValues() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 1, 1, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1);")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFirstThirdAndSecondFourthValuesTheSame() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 2, 1, 2)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insets(1, 2);")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentFirstParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(1, 0, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsTop(1);")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentSecondParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 1, 0, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsLeft(1);")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentThirdParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0, 1, 0)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsBottom(1);")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithThreeZerosAndDifferentFourthParam() {
    insets.get().doTest(
      SIMPLIFY_FIX_NAME,
      jbuiInsets("Insets myInsets = <warning descr=\"Insets creation can be simplified\">JBUI.in<caret>sets(0, 0, 0, 1)</warning>;"),
      jbuiInsets("Insets myInsets = JBUI.insetsRight(1);")
    );
  }

  @Test
  public void testJBUIInsetsEmptyWithFourZerosAndStaticImportForInsets() {
    insets.get().doTest(
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
