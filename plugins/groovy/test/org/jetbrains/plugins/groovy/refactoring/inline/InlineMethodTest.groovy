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

import com.intellij.lang.ASTNode
import com.intellij.lang.refactoring.InlineHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.inline.GenericInlineHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.Assert
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.util.TestUtils
class InlineMethodTest extends LightJavaCodeInsightFixtureTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/refactoring/inlineMethod/"

  void testAbstr1() throws Throwable { doTest() }

  void testBlock1() throws Throwable { doTest() }

  void testBlock2() throws Throwable { doTest() }

  void testBlock3() throws Throwable { doTest() }

  void testBlock4() throws Throwable { doTest() }

  void testCase1() throws Throwable { doTest() }

  void testCase2() throws Throwable { doTest() }

  void testClos_arg1() throws Throwable { doTest() }

  void testClos_arg2() throws Throwable { doTest() }

  void testClos_arg3() throws Throwable { doTest() }

  void testCond() throws Throwable { doTest() }

  void _testExpr1() throws Throwable { doTest() }

  void testExpr2() throws Throwable { doTest() }

  void testExpr3() throws Throwable { doTest() }

  void _testExpr4() throws Throwable { doTest() }

  void testFact() throws Throwable { doTest() }

  void testFact2() throws Throwable { doTest() }

  void testInit1() throws Throwable { doTest() }

  void testMap_arg1() throws Throwable { doTest() }

  void testQual1() throws Throwable { doTest() }

  void testQual2() throws Throwable { doTest() }

  void testQual3() throws Throwable { doTest() }

  void testQual4() throws Throwable { doTest() }

  void testQual5() throws Throwable { doTest() }

  void testRef1() throws Throwable { doTest() }

  void testRename1() throws Throwable { doTest() }

  void testRename2() throws Throwable { doTest() }

  void testRet1() throws Throwable { doTest() }

  void testRet2() throws Throwable { doTest() }

  void testRet3() throws Throwable { doTest() }

  void testRet4() throws Throwable { doTest() }

  void testRet5() throws Throwable { doTest() }

  void testTail1() throws Throwable { doTest() }

  void testTail1_1() throws Throwable { doTest() }

  void testTail2() throws Throwable { doTest() }

  void testTail3() throws Throwable { doTest() }

  void testTail4() throws Throwable { doTest() }

  void testTail5() throws Throwable { doTest() }

  void testTail6() throws Throwable { doTest() }

  void testVen_tail() throws Throwable { doTest() }

  void testVen_tail2() throws Throwable { doTest() }

  void testVoid() throws Throwable { doTest() }

  void testExpressionInParameter() throws Throwable { doTest() }

  void testFinalParameter() throws Throwable { doTest() }

  void testParameterIsUsedAfterCall() throws Throwable { doTest() }

  void testFieldAsParameter() throws Throwable { doTest() }

  void testWritableVariable() throws Throwable { doTest() }

  void testSingleExpression() { doTest() }

  void testNamedArg() { doTest() }

  void _testInlineInGString() throws Throwable { doTest() }

  void testDontRemoveReturnValueExpr() { doTest() }

  void testDontRemoveLastStatement() { doTest() }

  void testSideEffectInitializer() { doTest() }

  void testVarargs() { doTest() }

  void testTypeParameterDeclaredInFile() { doTest() }

  void testBadReturns() { doTest() }

  void testInlineAll() {
    doInlineAllTest()
  }

  private void doInlineAllTest() {
    doTest(new GroovyInlineHandler() {
      @Override
      InlineHandler.Settings prepareInlineElement(@NotNull PsiElement element, Editor editor, boolean invokedOnReference) {
        return { false } as InlineHandler.Settings
      }
    })
  }

  void testInlineNamedArgs() { doTest() }

  void testInlineVarargs() { doTest() }

  void testCannotInlineMethodRef() {
    try {
      doInlineAllTest()
      assert false
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Cannot inline reference 'new A().&foo'", e.message)
    }
  }

  void testSuperCall() { doTest() }

  protected void doTest() {
    doTest(new GroovyInlineHandler())
  }

  protected void doTest(InlineHandler handler) {
    doInlineTest(myFixture, testDataPath + getTestName(true) + ".test", handler)
  }

  static void doInlineTest(final JavaCodeInsightTestFixture fixture,
                           final String testFile,
                           InlineHandler inlineHandler) {
    final List<String> data = TestUtils.readInput(testFile)
    String fileText = data.get(0)

    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)

    final PsiFile file = fixture.file
    final Editor editor = fixture.editor
    indentationToNode = file.node
    int startOffset = editor.selectionModel.selectionStart
    int endOffset = editor.selectionModel.selectionEnd
    editor.caretModel.moveToOffset(endOffset)

    GroovyPsiElement selectedArea = PsiImplUtil.findElementInRange(file, startOffset, endOffset, GrReferenceExpression.class)
    if (selectedArea == null) {
      PsiElement identifier = PsiImplUtil.findElementInRange(file, startOffset, endOffset, PsiElement.class)
      if (identifier != null) {
        if (identifier.parent instanceof GrVariable) {
          selectedArea = (GroovyPsiElement)identifier.parent
        }
        else if (identifier instanceof GrMethod) {
          selectedArea = identifier
        }
        else {
          this.assertTrue("Selected area doesn't point to method or variable", false)
        }
      }
    }
    Assert.assertNotNull("Selected area reference points to nothing", selectedArea)
    PsiElement element = selectedArea instanceof GrExpression ? selectedArea.reference.resolve() : selectedArea
    Assert.assertNotNull("Cannot resolve selected reference expression", element)

    try {
      GenericInlineHandler.invoke(element, editor, inlineHandler)
      editor.selectionModel.removeSelection()
      fixture.checkResult(data.get(1), true)
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(data.get(1), "FAIL: " + e.message)
    }
  }

  private static void setIndentationToNode(ASTNode element){
    if (element instanceof TreeElement) {
      CodeEditUtil.setOldIndentation(((TreeElement) element), 0)
    }
    for (ASTNode node : element.getChildren(null)) {
      indentationToNode = node
    }
  }

}
