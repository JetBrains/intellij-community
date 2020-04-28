/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.VisibilityUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.refactoring.introduce.constant.GrIntroduceConstantHandler
import org.jetbrains.plugins.groovy.refactoring.introduce.constant.GrIntroduceConstantSettings
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
class IntroduceConstantTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    TestUtils.testDataPath + "refactoring/introduceConstant/"
  }

  void testSimple() {
    doTest()
  }

  void testReplaceAllOccurences() {
    doTest()
  }

  void testEscalateVisibility() {
    doTest("Other", true, false, VisibilityUtil.ESCALATE_VISIBILITY)
  }

  void testInsertInEnum() {
    doTest("Planet", false, false, PsiModifier.PROTECTED)
  }

  void testInsertInInterface() {
    doTest("MyInterface", false, false, PsiModifier.PROTECTED)
  }

  void testTupleDeclaration() {
    doTest("Test", true, false, PsiModifier.PUBLIC)
  }

  void testStringPart() {
    doTest()
  }

  void testAnonymousClass() {
    doTest()
  }

  void testFieldWithClassName() {
    doTest()
  }

  void testLocalVarRef() {
    doTest()
  }

  private void doTest() {
    doTest(null, true, true, PsiModifier.PUBLIC)
  }

  private void doTest(@Nullable String targetClassName, boolean replaceAllOccurrences, boolean useExplicitType, String modifier) {
    myFixture.configureByFile(getTestName(false) + ".groovy")



    final GrIntroduceConstantHandler handler = new GrIntroduceConstantHandler()
    final Editor editor = myFixture.getEditor()

    final GrExpression expression = findExpression(myFixture)
    final GrVariable variable = findVariable(myFixture)
    final StringPartInfo stringPart = findStringPart(myFixture)
    PsiElement[] scopes = handler.findPossibleScopes(expression, variable, stringPart, editor)
    final GrIntroduceContext context = handler.getContext(getProject(), editor, expression, variable, stringPart, scopes[0])

    PsiClass targetClass = targetClassName == null ? GrIntroduceConstantHandler.findContainingClass(context)
                                                   : myFixture.findClass(targetClassName)
    assertNotNull("target class is null", targetClass)

    def type = getType(useExplicitType, expression, variable, stringPart)
    final GrIntroduceConstantSettings settings = new MockIntroduceConstantSettings(targetClass, replaceAllOccurrences, type, modifier)

    WriteCommandAction.runWriteCommandAction(project) {
      handler.runRefactoring(context, settings)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true)
  }

  private static PsiType getType(boolean useExplicitType, GrExpression expression, GrVariable variable, StringPartInfo stringPart) {
    if (!useExplicitType) {
      return null
    }
    return expression != null ? expression.getType() :
           variable   != null ? variable.getType() :
                                stringPart.getLiteral().getType()
  }

  @Nullable
  static GrVariable findVariable(JavaCodeInsightTestFixture fixture) {
    final Editor editor = fixture.getEditor()
    final int start = editor.getSelectionModel().getSelectionStart()
    final int end = editor.getSelectionModel().getSelectionEnd()
    return GrIntroduceHandlerBase.findVariable(fixture.getFile(), start, end)
  }

  @Nullable
  static GrExpression findExpression(JavaCodeInsightTestFixture fixture) {
    final Editor editor = fixture.getEditor()
    final int start = editor.getSelectionModel().getSelectionStart()
    final int end = editor.getSelectionModel().getSelectionEnd()
    return GrIntroduceHandlerBase.findExpression(fixture.getFile(), start, end)
  }

  @Nullable
  static StringPartInfo findStringPart(JavaCodeInsightTestFixture fixture) {
    final Editor editor = fixture.getEditor()
    final int start = editor.getSelectionModel().getSelectionStart()
    final int end = editor.getSelectionModel().getSelectionEnd()
    return StringPartInfo.findStringPart(fixture.getFile(), start, end)
  }

  private static class MockIntroduceConstantSettings implements GrIntroduceConstantSettings {
    private final PsiClass myTargetClass
    private final boolean myReplaceAllOccurrences
    private final PsiType mySelectedType
    private final String myModifier

    private MockIntroduceConstantSettings(@NotNull PsiClass targetClass,
                                          boolean replaceAllOccurrences,
                                          @Nullable PsiType selectedType,
                                          String modifier) {
      myTargetClass = targetClass
      myReplaceAllOccurrences = replaceAllOccurrences
      mySelectedType = selectedType
      myModifier = modifier
    }

    @Override
    String getVisibilityModifier() {
      return myModifier
    }

    @Override
    PsiClass getTargetClass() {
      return myTargetClass
    }

    @Override
    String getName() {
      return "CONST"
    }

    @Override
    boolean replaceAllOccurrences() {
      return myReplaceAllOccurrences
    }

    @Override
    PsiType getSelectedType() {
      return mySelectedType
    }
  }
}
