// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class GroovyRangeTypeCheckTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/inspections/rangeTypeCheck";
  }

  public void doTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy");

    final int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiElement atCaret = myFixture.getFile().findElementAt(offset);
    final GrRangeExpression range = PsiTreeUtil.getParentOfType(atCaret, GrRangeExpression.class);
    final GroovyRangeTypeCheckInspection inspection = new GroovyRangeTypeCheckInspection();

    final LocalQuickFix fix = inspection.buildFix(range);

    LocalQuickFix[] fixes = {fix};
    final ProblemDescriptor descriptor = InspectionManager.getInstance(getProject()).createProblemDescriptor(range, "bla-bla", false, fixes, ProblemHighlightType.WEAK_WARNING);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      fix.applyFix(myFixture.getProject(), descriptor);
    });


    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testNotComparable() { doTest(); }

  public void testNext() {doTest(); }

  public void testAll() {doTest(); }
}
