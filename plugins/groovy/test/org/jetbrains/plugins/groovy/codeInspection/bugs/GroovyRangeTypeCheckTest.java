// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GroovyRangeTypeCheckTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/inspections/rangeTypeCheck");
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");

    EdtTestUtil.runInEdtAndWait(() -> {
      final int offset = myFixture.getEditor().getCaretModel().getOffset();
      final PsiElement atCaret = myFixture.getFile().findElementAt(offset);
      final GrRangeExpression range = PsiTreeUtil.getParentOfType(atCaret, GrRangeExpression.class);
      final GroovyRangeTypeCheckInspection inspection = new GroovyRangeTypeCheckInspection();

      final LocalQuickFix fix = inspection.buildFix(range);

      LocalQuickFix[] fixes = {fix};
      final ProblemDescriptor descriptor = InspectionManager.getInstance(myFixture.getProject()).createProblemDescriptor(range, "bla-bla", false, fixes, ProblemHighlightType.WEAK_WARNING);
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        fix.applyFix(myFixture.getProject(), descriptor);
      });

      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
    });
  }

  @Test
  public void testNotComparable() {
    doTest();
  }

  @Test
  public void testNext() {
    doTest();
  }

  @Test
  public void testAll() {
    doTest();
  }
}
