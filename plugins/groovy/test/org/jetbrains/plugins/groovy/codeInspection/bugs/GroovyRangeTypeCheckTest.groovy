/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class GroovyRangeTypeCheckTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + 'groovy/inspections/rangeTypeCheck'
  }

  public void doTest() {
    myFixture.configureByFile getTestName(false) + '.groovy'

    final int offset = myFixture.getEditor().getCaretModel().getOffset()
    final PsiElement atCaret = myFixture.getFile().findElementAt(offset)
    final GrRangeExpression range = PsiTreeUtil.getParentOfType(atCaret, GrRangeExpression)
    final GroovyRangeTypeCheckInspection inspection = new GroovyRangeTypeCheckInspection()

    GroovyFix fix = inspection.buildFix(range)

    final ProblemDescriptor descriptor = InspectionManager.getInstance(project).createProblemDescriptor(range, "bla-bla", fix, ProblemHighlightType.WEAK_WARNING);
    ApplicationManager.application.runWriteAction(new Runnable() {
      @Override
      void run() {
        fix.applyFix myFixture.project, descriptor
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      }
    })


    myFixture.checkResultByFile getTestName(false) + '_after.groovy'
  }

  void testNotComparable() {doTest()}

  void testNext() {doTest()}

  void testAll() {doTest()}
}
