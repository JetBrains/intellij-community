/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.ASTNode;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.refactoring.inline.GenericInlineHandler;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import junit.framework.Assert;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class InlineMethodTest extends LightCodeInsightFixtureTestCase {
  public static final String CARET_MARKER = "<caret>";
  public static final String BEGIN_MARKER = "<begin>";
  public static final String END_MARKER = "<end>";

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/inlineMethod/";
  }

  public void testAbstr1() throws Throwable { doTest(); }
  public void testBlock1() throws Throwable { doTest(); }
  public void testBlock2() throws Throwable { doTest(); }
  public void testBlock3() throws Throwable { doTest(); }
  public void testBlock4() throws Throwable { doTest(); }
  public void testCase1() throws Throwable { doTest(); }
  public void testCase2() throws Throwable { doTest(); }
  public void testClos_arg1() throws Throwable { doTest(); }
  public void testClos_arg2() throws Throwable { doTest(); }
  public void testClos_arg3() throws Throwable { doTest(); }
  public void testCond() throws Throwable { doTest(); }
  public void testExpr1() throws Throwable { doTest(); }
  public void testExpr2() throws Throwable { doTest(); }
  public void testExpr3() throws Throwable { doTest(); }
  public void testExpr4() throws Throwable { doTest(); }
  public void testFact() throws Throwable { doTest(); }
  public void testFact2() throws Throwable { doTest(); }
  public void testInit1() throws Throwable { doTest(); }
  public void testMap_arg1() throws Throwable { doTest(); }
  public void testQual1() throws Throwable { doTest(); }
  public void testQual2() throws Throwable { doTest(); }
  public void testQual3() throws Throwable { doTest(); }
  public void testQual4() throws Throwable { doTest(); }
  public void testQual5() throws Throwable { doTest(); }
  public void testRef1() throws Throwable { doTest(); }
  public void testRename1() throws Throwable { doTest(); }
  public void testRename2() throws Throwable { doTest(); }
  public void testRet1() throws Throwable { doTest(); }
  public void testRet2() throws Throwable { doTest(); }
  public void testRet3() throws Throwable { doTest(); }
  public void testRet4() throws Throwable { doTest(); }
  public void testRet5() throws Throwable { doTest(); }
  public void testTail1() throws Throwable { doTest(); }
  public void testTail1_1() throws Throwable { doTest(); }
  public void testTail2() throws Throwable { doTest(); }
  public void testTail3() throws Throwable { doTest(); }
  public void testTail4() throws Throwable { doTest(); }
  public void testTail5() throws Throwable { doTest(); }
  public void testTail6() throws Throwable { doTest(); }
  public void testVen_tail() throws Throwable { doTest(); }
  public void testVen_tail2() throws Throwable { doTest(); }
  public void testVoid() throws Throwable { doTest(); }
  public void testExpressionInParameter() throws Throwable {doTest();}
  public void testFinalParameter() throws Throwable { doTest(); }
  public void testParameterIsUsedAfterCall() throws Throwable { doTest(); }
  public void testFieldAsParameter() throws Throwable { doTest(); }
  public void testWritableVariable() throws Throwable { doTest(); }
  public void testSingleExpression() {doTest();}
  public void _testInlineInGString() throws Throwable {doTest(); }

  public void testInlineAll() throws Throwable {
    doTest(new GroovyInlineHandler() {
      @Override
      public Settings prepareInlineElement(PsiElement element, Editor editor, boolean invokedOnReference) {
        return new Settings() {
          public boolean isOnlyOneReferenceToInline() {
            return false;
          }
        };
      }
    });
  }

  protected void doTest() {
    doTest(new GroovyInlineHandler());

  }

  protected void doTest(InlineHandler handler) {
    doInlineTest(myFixture, getTestDataPath() + getTestName(true) + ".test", false, handler);
  }

  public static void doInlineTest(final JavaCodeInsightTestFixture fixture, final String testFile, boolean withCaret,
                                  InlineHandler inlineHandler) {
    final List<String> data = TestUtils.readInput(testFile);
    String fileText = data.get(0);

    int startOffset = fileText.indexOf(BEGIN_MARKER);
    fileText = TestUtils.removeBeginMarker(fileText);
    int endOffset = fileText.indexOf(END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);

    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    final PsiFile file = fixture.getFile();
    final Editor myEditor = fixture.getEditor();
    setIndentationToNode(file.getNode());
    myEditor.getSelectionModel().setSelection(startOffset, endOffset);
    myEditor.getCaretModel().moveToOffset(endOffset);

    GroovyPsiElement selectedArea = GroovyRefactoringUtil.findElementInRange(((GroovyFileBase) file), startOffset, endOffset, GrReferenceExpression.class);
    if (selectedArea == null) {
    PsiElement identifier = GroovyRefactoringUtil.findElementInRange(((GroovyFileBase) file), startOffset, endOffset, PsiElement.class);
    if (identifier != null){
      Assert.assertTrue("Selected area doesn't point to method", identifier.getParent() instanceof GrVariable);
      selectedArea = (GroovyPsiElement)identifier.getParent();
    }
  }
    Assert.assertNotNull("Selected area reference points to nothing", selectedArea);
    PsiElement element = selectedArea instanceof GrExpression ? selectedArea.getReference().resolve() : selectedArea;
    Assert.assertNotNull("Cannot resolve selected reference expression", element);

    // handling inline refactoring
    GenericInlineHandler.invoke(element, myEditor, inlineHandler);
    String result = myEditor.getDocument().getText();
    String invokedResult = GroovyInlineMethodUtil.getInvokedResult();
    final int caretOffset = myEditor.getCaretModel().getOffset();
    result = "ok".equals(invokedResult) ?
      (withCaret ? result.substring(0, caretOffset) + CARET_MARKER + result.substring(caretOffset) : result) :
      "FAIL: " + invokedResult;

    assertEquals(data.get(1), result);
  }

  private static void setIndentationToNode(ASTNode element){
    if (element instanceof TreeElement) {
      CodeEditUtil.setOldIndentation(((TreeElement) element), 0);
    }
    for (ASTNode node : element.getChildren(null)) {
      setIndentationToNode(node);
    }
  }

}
