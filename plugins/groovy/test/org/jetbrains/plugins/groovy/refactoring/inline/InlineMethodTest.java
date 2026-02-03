// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.ASTNode;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.GenericInlineHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class InlineMethodTest extends LightJavaCodeInsightFixtureTestCase {
  public void testAbstr1() { doTest(); }

  public void testBlock1() { doTest(); }

  public void testBlock2() { doTest(); }

  public void testBlock3() { doTest(); }

  public void testBlock4() { doTest(); }

  public void testCase1() { doTest(); }

  public void testCase2() { doTest(); }

  public void testClos_arg1() { doTest(); }

  public void testClos_arg2() { doTest(); }

  public void testClos_arg3() { doTest(); }

  public void testCond() { doTest(); }

  public void _testExpr1() { doTest(); }

  public void testExpr2() { doTest(); }

  public void testExpr3() { doTest(); }

  public void _testExpr4() { doTest(); }

  public void testFact() { doTest(); }

  public void testFact2() { doTest(); }

  public void testInit1() { doTest(); }

  public void testMap_arg1() { doTest(); }

  public void testQual1() { doTest(); }

  public void testQual2() { doTest(); }

  public void testQual3() { doTest(); }

  public void testQual4() { doTest(); }

  public void testQual5() { doTest(); }

  public void testRef1() { doTest(); }

  public void testRename1() { doTest(); }

  public void testRename2() { doTest(); }

  public void testRet1() { doTest(); }

  public void testRet2() { doTest(); }

  public void testRet3() { doTest(); }

  public void testRet4() { doTest(); }

  public void testRet5() { doTest(); }

  public void testTail1() { doTest(); }

  public void testTail1_1() { doTest(); }

  public void testTail2() { doTest(); }

  public void testTail3() { doTest(); }

  public void testTail4() { doTest(); }

  public void testTail5() { doTest(); }

  public void testTail6() { doTest(); }

  public void testVen_tail() { doTest(); }

  public void testVen_tail2() { doTest(); }

  public void testVoid() { doTest(); }

  public void testExpressionInParameter() { doTest(); }

  public void testFinalParameter() { doTest(); }

  public void testParameterIsUsedAfterCall() { doTest(); }

  public void testFieldAsParameter() { doTest(); }

  public void testWritableVariable() { doTest(); }

  public void testSingleExpression() { doTest(); }

  public void testNamedArg() { doTest(); }

  public void _testInlineInGString() { doTest(); }

  public void testDontRemoveReturnValueExpr() { doTest(); }

  public void testDontRemoveLastStatement() { doTest(); }

  public void testSideEffectInitializer() { doTest(); }

  public void testVarargs() { doTest(); }

  public void testTypeParameterDeclaredInFile() { doTest(); }

  public void testBadReturns() { doTest(); }

  public void testInlineAll() {
    doInlineAllTest();
  }

  private void doInlineAllTest() {
    doTest(new GroovyInlineHandler() {
      @Override
      public Settings prepareInlineElement(@NotNull PsiElement element, Editor editor, boolean invokedOnReference) {
        return () -> false;
      }
    });
  }

  public void testInlineNamedArgs() { doTest(); }

  public void testInlineVarargs() { doTest(); }

  public void testCannotInlineMethodRef() {
    try {
      doInlineAllTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Cannot inline reference 'new A().&foo'", e.getMessage());
    }
  }

  public void testSuperCall() { doTest(); }

  protected void doTest() {
    doTest(new GroovyInlineHandler());
  }

  protected void doTest(InlineHandler handler) {
    doInlineTest(myFixture, getTestDataPath() + getTestName(true) + ".test", handler);
  }

  public static void doInlineTest(final JavaCodeInsightTestFixture fixture, final String testFile, InlineHandler inlineHandler) {
    final List<String> data = TestUtils.readInput(testFile);
    String fileText = data.get(0);

    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    final PsiFile file = fixture.getFile();
    final Editor editor = fixture.getEditor();
    setIndentationToNode(file.getNode());
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();
    editor.getCaretModel().moveToOffset(endOffset);

    GroovyPsiElement selectedArea = PsiImplUtil.findElementInRange(file, startOffset, endOffset, GrReferenceExpression.class);
    if (selectedArea == null) {
      PsiElement identifier = PsiImplUtil.findElementInRange(file, startOffset, endOffset, PsiElement.class);
      if (identifier != null) {
        if (identifier.getParent() instanceof GrVariable) {
          selectedArea = (GroovyPsiElement)identifier.getParent();
        }
        else if (identifier instanceof GrMethod) {
          selectedArea = ((GroovyPsiElement)(identifier));
        }
        else { 
          fail("Selected area doesn't point to method or variable");
        }
      }
    }

    assertNotNull("Selected area reference points to nothing", selectedArea);
    PsiElement element = selectedArea instanceof GrExpression ? selectedArea.getReference().resolve() : selectedArea;
    assertNotNull("Cannot resolve selected reference expression", element);

    try {
      GenericInlineHandler.invoke(element, editor, inlineHandler);
      editor.getSelectionModel().removeSelection();
      fixture.checkResult(data.get(1), true);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(data.get(1), "FAIL: " + e.getMessage());
    }
  }

  private static void setIndentationToNode(ASTNode element) {
    if (element instanceof TreeElement) {
      CodeEditUtil.setOldIndentation(((TreeElement)element), 0);
    }

    for (ASTNode node : element.getChildren(null)) {
      setIndentationToNode(node);
    }
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/inlineMethod/";
  }
}
