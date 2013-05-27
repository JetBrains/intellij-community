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
import com.intellij.psi.PsiType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase
import org.jetbrains.plugins.groovy.intentions.declaration.GrIntroduceLocalVariableIntention
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableDialog
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableSettings
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyVariableValidator
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
    protected void processIntention(PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
      setSelection(editor, getTargetExpression(element));
      MockSettings settings = new MockSettings(false, "varName", null, false)
      new MockGrIntroduceVariableHandler(settings).invoke(project, editor, element.containingFile, null);
    }
  }

  static class MockGrIntroduceVariableHandler extends GrIntroduceVariableHandler {
    private final MockSettings mySettings

    MockGrIntroduceVariableHandler(MockSettings settings) {
      mySettings = settings
    }

    @NotNull
    @Override
    protected GroovyIntroduceVariableDialog getDialog(@NotNull GrIntroduceContext context) {
      new MockGrIntroduceVariableDialog(context, new GroovyVariableValidator(context), mySettings)
    }
  }

  static class MockGrIntroduceVariableDialog extends GroovyIntroduceVariableDialog {
    private final MockSettings mySettings

    MockGrIntroduceVariableDialog(GrIntroduceContext context, GroovyVariableValidator validator, MockSettings settings) {
      super(context, validator)
      mySettings = settings
    }

    @Override
    void show() {
      close(0)
    }

    @Override
    MockSettings getSettings() { mySettings }

    @Override
    boolean isOK() {
      true
    }
  }

  static class MockSettings implements GroovyIntroduceVariableSettings {
    private final boolean myFinal
    private final String myName
    private final boolean myAllOccurrences
    private final PsiType myType

    MockSettings(final boolean isFinal, final String name, PsiType type, boolean allOccurrences) {
      myFinal = isFinal
      myName = name
      myType = type
      myAllOccurrences = allOccurrences
    }

    @Override
    boolean isDeclareFinal() {
      return myFinal
    }

    @Override
    String getName() {
        myName
    }

    @Override
    boolean replaceAllOccurrences() {
      return myAllOccurrences
    }

    @Override
    PsiType getSelectedType() {
      return myType
    }
  }
}
