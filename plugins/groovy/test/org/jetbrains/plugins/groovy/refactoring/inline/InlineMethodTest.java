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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class InlineMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/inlineMethod/");
  }

  @Test
  public void testAbstr1() {
    doTest();
  }

  @Test
  public void testBlock1() {
    doTest();
  }

  @Test
  public void testBlock2() {
    doTest();
  }

  @Test
  public void testBlock3() {
    doTest();
  }

  @Test
  public void testBlock4() {
    doTest();
  }

  @Test
  public void testCase1() {
    doTest();
  }

  @Test
  public void testCase2() {
    doTest();
  }

  @Test
  public void testClos_arg1() {
    doTest();
  }

  @Test
  public void testClos_arg2() {
    doTest();
  }

  @Test
  public void testClos_arg3() {
    doTest();
  }

  @Test
  public void testCond() {
    doTest();
  }

  public void _testExpr1() { doTest(); }

  @Test
  public void testExpr2() {
    doTest();
  }

  @Test
  public void testExpr3() {
    doTest();
  }

  public void _testExpr4() { doTest(); }

  @Test
  public void testFact() {
    doTest();
  }

  @Test
  public void testFact2() {
    doTest();
  }

  @Test
  public void testInit1() {
    doTest();
  }

  @Test
  public void testMap_arg1() {
    doTest();
  }

  @Test
  public void testQual1() {
    doTest();
  }

  @Test
  public void testQual2() {
    doTest();
  }

  @Test
  public void testQual3() {
    doTest();
  }

  @Test
  public void testQual4() {
    doTest();
  }

  @Test
  public void testQual5() {
    doTest();
  }

  @Test
  public void testRef1() {
    doTest();
  }

  @Test
  public void testRename1() {
    doTest();
  }

  @Test
  public void testRename2() {
    doTest();
  }

  @Test
  public void testRet1() {
    doTest();
  }

  @Test
  public void testRet2() {
    doTest();
  }

  @Test
  public void testRet3() {
    doTest();
  }

  @Test
  public void testRet4() {
    doTest();
  }

  @Test
  public void testRet5() {
    doTest();
  }

  @Test
  public void testTail1() {
    doTest();
  }

  @Test
  public void testTail1_1() {
    doTest();
  }

  @Test
  public void testTail2() {
    doTest();
  }

  @Test
  public void testTail3() {
    doTest();
  }

  @Test
  public void testTail4() {
    doTest();
  }

  @Test
  public void testTail5() {
    doTest();
  }

  @Test
  public void testTail6() {
    doTest();
  }

  @Test
  public void testVen_tail() {
    doTest();
  }

  @Test
  public void testVen_tail2() {
    doTest();
  }

  @Test
  public void testVoid() {
    doTest();
  }

  @Test
  public void testExpressionInParameter() {
    doTest();
  }

  @Test
  public void testFinalParameter() {
    doTest();
  }

  @Test
  public void testParameterIsUsedAfterCall() {
    doTest();
  }

  @Test
  public void testFieldAsParameter() {
    doTest();
  }

  @Test
  public void testWritableVariable() {
    doTest();
  }

  @Test
  public void testSingleExpression() {
    doTest();
  }

  @Test
  public void testNamedArg() {
    doTest();
  }

  public void _testInlineInGString() { doTest(); }

  @Test
  public void testDontRemoveReturnValueExpr() {
    doTest();
  }

  @Test
  public void testDontRemoveLastStatement() {
    doTest();
  }

  @Test
  public void testSideEffectInitializer() {
    doTest();
  }

  @Test
  public void testVarargs() {
    doTest();
  }

  @Test
  public void testTypeParameterDeclaredInFile() {
    doTest();
  }

  @Test
  public void testBadReturns() {
    doTest();
  }

  @Test
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

  @Test
  public void testInlineNamedArgs() {
    doTest();
  }

  @Test
  public void testInlineVarargs() {
    doTest();
  }

  @Test
  public void testCannotInlineMethodRef() {
    try {
      doInlineAllTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Cannot inline reference 'new A().&foo'", e.getMessage());
    }
  }

  @Test
  public void testSuperCall() {
    doTest();
  }

  protected void doTest() {
    doTest(new GroovyInlineHandler());
  }

  protected void doTest(InlineHandler handler) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doInlineTest(myFixture, myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test", handler);
  }

  public static void doInlineTest(final JavaCodeInsightTestFixture fixture, final String testFile, InlineHandler inlineHandler) {
    final List<String> data = TestUtils.readInput(testFile);
    String fileText = data.get(0);

    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    EdtTestUtil.runInEdtAndWait(() -> {
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

      assertNotNull(selectedArea, "Selected area reference points to nothing");
      PsiElement element = selectedArea instanceof GrExpression ? selectedArea.getReference().resolve() : selectedArea;
      assertNotNull(element, "Cannot resolve selected reference expression");

      try {
        GenericInlineHandler.invoke(element, editor, inlineHandler);
        editor.getSelectionModel().removeSelection();
        fixture.checkResult(data.get(1), true);
      }
      catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        assertEquals(data.get(1), "FAIL: " + e.getMessage());
      }
    });
  }

  private static void setIndentationToNode(ASTNode element) {
    if (element instanceof TreeElement) {
      CodeEditUtil.setOldIndentation(((TreeElement)element), 0);
    }

    for (ASTNode node : element.getChildren(null)) {
      setIndentationToNode(node);
    }
  }
}
