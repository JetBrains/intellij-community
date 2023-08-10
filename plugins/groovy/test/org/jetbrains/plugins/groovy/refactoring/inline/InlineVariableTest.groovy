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

package org.jetbrains.plugins.groovy.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.util.TestUtils
class InlineVariableTest extends LightJavaCodeInsightFixtureTestCase {
  final String basePath = TestUtils.testDataPath + "groovy/refactoring/inlineLocal/"

  void testGRVY_1232() { doTest() }

  void testGRVY_1248() { doTest() }

  void testVar1() { doTest() }

  void testVar2() { doTest() }

  void testVar3() { doTest() }

  void testVar4() { doTest() }

  void testVar5() { doTest() }

  void testVar6() { doTest() }

  void testVarInGString() { doTest() }

  void testVarInGString2() { doTest() }

  void testVarInGString3() { doTest() }

  void testVarInGString4() { doTest() }

  void testField() { doFieldTest() }

  void testPartial1() { doTest() }

  void testPartial2() { doTest() }

  void testPartial3() { doTest() }

  void testPartial4() { doTest() }

  void testClosure1() { doTest() }

  void testClosure2() { doTest() }

  void testClosure3() { doTest() }

  void testAnonymousClass1() { doTest() }

  void testRegexInCommandArg1() { doTest() }

  void testRegexInCommandArg2() { doTest() }

  void testRegexInCommandArg3() { doTest() }

  void testRegexInCommandArg4() { doTest() }

  void testRegexInCommandArg5() { doTest() }

  void testUndefinedVarInline() { doTest() }

  void testImplicitCast1() { doTest() }

  void testImplicitCast2() { doTest() }

  protected void doFieldTest() {
    InlineMethodTest.doInlineTest(myFixture, testDataPath + getTestName(true) + ".test", new GroovyInlineHandler())
  }

  private void doTest()  {
    doTest(false)
  }

  private void doTest(final boolean inlineDef) {
    final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
    String fileText = data.get(0)

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)

    final Editor editor = myFixture.editor
    final PsiFile file = myFixture.file
    setIndentationToNode(file.node)

    int startOffset = editor.selectionModel.selectionStart
    int endOffset = editor.selectionModel.selectionEnd
    editor.caretModel.moveToOffset(endOffset)

    GroovyPsiElement selectedArea =
      PsiImplUtil.findElementInRange(file, startOffset, endOffset, GrReferenceExpression.class)
    if (selectedArea == null) {
      PsiElement identifier = PsiImplUtil.findElementInRange(file, startOffset, endOffset, PsiElement.class)
      if (identifier != null) {
        assertTrue("Selected area doesn't point to var", identifier.parent instanceof GrVariable)
        selectedArea = (GroovyPsiElement)identifier.parent
      }
    }
    assertNotNull("Selected area reference points to nothing", selectedArea)
    PsiElement element = selectedArea instanceof GrExpression ? selectedArea.reference.resolve() : selectedArea
    assertNotNull("Cannot resolve selected reference expression", element)

    try {
      if (!inlineDef) {
        performInline(project, editor)
      }
      else {
        performDefInline(project, editor)
      }
      editor.selectionModel.removeSelection()
      myFixture.checkResult(data.get(1), true)
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(data.get(1), "FAIL: " + e.message)
    }
  }

  static void performInline(Project project, Editor editor) {
    PsiElement element = TargetElementUtil.findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED |
                                                                         TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED)
    assertInstanceOf(element, GrVariable)

    GroovyInlineLocalHandler.invoke(project, editor, element as GrVariable)
  }

  static void performDefInline(Project project, Editor editor) {
    PsiReference reference = TargetElementUtil.findReference(editor)
    assertTrue(reference instanceof PsiReferenceExpression)
    final PsiElement local = reference.resolve()
    assertTrue(local instanceof PsiLocalVariable)

    GroovyInlineLocalHandler.invoke(project, editor, (GrVariable)local)
  }

  private static void setIndentationToNode(ASTNode element){
    if (element instanceof TreeElement) {
      CodeEditUtil.setOldIndentation(((TreeElement)element), 0)
    }
    for (ASTNode node : element.getChildren(null)) {
      setIndentationToNode(node)
    }
  }

}
