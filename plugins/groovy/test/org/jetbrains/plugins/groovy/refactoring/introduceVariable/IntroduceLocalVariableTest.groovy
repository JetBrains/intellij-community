/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduceVariable

import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase
import org.jetbrains.plugins.groovy.intentions.declaration.GrIntroduceLocalVariableIntention
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author siosio
 */
public class IntroduceLocalVariableTest extends GrIntentionTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/introduceLocalVariable/";
  }

  public void testMethodCall1() {doTest()}
  public void testMethodCall2() {doTest()}
  public void testMethodCall3() {doTest()}
  public void testMethodCall4() {doTest()}
  public void testConstructor() {doTest()}
  public void testClosure1() {doTest()}
  public void testClosure2() {doTest()}

  protected void doTest() {
    myFixture.configureByFile("${getTestName(false)}.groovy")
    def intentions = myFixture.availableIntentions

    for (intention in intentions) {
      if (intention instanceof IntentionActionWrapper) intention = intention.delegate
      if (intention instanceof GrIntroduceLocalVariableIntention) {
        new MockGrIntroduceLocalVariableIntention().invoke(myFixture.project, myFixture.editor, myFixture.file)
      }
    }
    myFixture.checkResultByFile("${getTestName(false)}-after.groovy")
  }

  static class MockGrIntroduceLocalVariableIntention extends GrIntroduceLocalVariableIntention {
    @Override
    protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
      setSelection(editor, getTargetExpression(element));
      MockSettings settings = new MockSettings(false, "varName", null, false)
      new MockGrIntroduceVariableHandler(settings).invoke(project, editor, element.containingFile, null);
    }
  }
}
