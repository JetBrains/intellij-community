// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainText;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.junit.ComparisonFailure;

public class ExpectedHighlightingDataFunctionalTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testExpectedWarning() {
    doTest("<warning descr=\"Magic problem\">just some text</warning>", new MySimpleInspection());
  }

  public void testUnexpectedWarning() {
    try {
      doTest("just some text", new MySimpleInspection());
    }
    catch (ComparisonFailure failure) {
      String message = failure.getMessage();
      String firstLine = message.split("\n")[0];
      assertSameLines("hello.txt: extra (1:1/14): 'just some text' (Magic problem) [HighlightInfoTypeImpl[severity=WARNING, key=WARNING_ATTRIBUTES]]", firstLine);
    }
  }

  public void testDoubleMessage() {
    try {
      doTest("<warning descr=\"Magic problem\">just some text</warning>", new MyDoubleInspection());
    }
    catch (ComparisonFailure failure) {
      String message = failure.getMessage();
      String firstLine = message.split("\n")[0];
      assertSameLines("hello.txt: duplicated (1:1/14): 'just some text' (Magic problem)", firstLine);
    }
  }

  public void testDoNotCheckDoubleMessage() {
    ExpectedHighlightingData.expectedDuplicatedHighlighting(
      () -> doTest("<warning descr=\"Magic problem\">just some text</warning>", new MyDoubleInspection())
    );
  }

  public void testExpectedDuplicationWasNotFound() {
    try {
      ExpectedHighlightingData.expectedDuplicatedHighlighting(
        () -> doTest("<warning descr=\"Magic problem\">just some text</warning>", new MySimpleInspection())
      );
    }
    catch (IllegalStateException failure) {
      String message = failure.getMessage();
      assertSameLines(ExpectedHighlightingData.EXPECTED_DUPLICATION_MESSAGE, message);
    }
  }


  private void doTest(@NotNull String text, LocalInspectionTool inspection) {
    myFixture.configureByText("hello.txt", text);

    try {
      myFixture.enableInspections(inspection);
      myFixture.checkHighlighting();
    }
    finally {
      myFixture.disableInspections(inspection);
    }
  }

  private static void report(PsiPlainText content, @NotNull ProblemsHolder holder) {
    holder.registerProblem(content, "Magic problem", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  private static class MySimpleInspection extends MyBaseTestInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new PsiElementVisitor() {
        @Override
        public void visitPlainText(PsiPlainText content) {
          report(content, holder);
        }
      };
    }
  }

  private static class MyDoubleInspection extends MyBaseTestInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new PsiElementVisitor() {
        @Override
        public void visitPlainText(PsiPlainText content) {
          report(content, holder);
          report(content, holder);
        }
      };
    }
  }

  private abstract static class MyBaseTestInspection extends LocalInspectionTool {
    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
      return "test-inspection";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getGroupDisplayName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return getGroupDisplayName();
    }
  }
}
