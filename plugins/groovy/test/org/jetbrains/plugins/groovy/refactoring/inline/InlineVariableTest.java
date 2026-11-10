// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InlineVariableTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/inlineLocal/");
  }

  @Test
  public void testGRVY_1232() {
    doTest();
  }

  @Test
  public void testGRVY_1248() {
    doTest();
  }

  @Test
  public void testVar1() {
    doTest();
  }

  @Test
  public void testVar2() {
    doTest();
  }

  @Test
  public void testVar3() {
    doTest();
  }

  @Test
  public void testVar4() {
    doTest();
  }

  @Test
  public void testVar5() {
    doTest();
  }

  @Test
  public void testVar6() {
    doTest();
  }

  @Test
  public void testVarInGString() {
    doTest();
  }

  @Test
  public void testVarInGString2() {
    doTest();
  }

  @Test
  public void testVarInGString3() {
    doTest();
  }

  @Test
  public void testVarInGString4() {
    doTest();
  }

  @Test
  public void testField() {
    doFieldTest();
  }

  @Test
  public void testPartial1() {
    doTest();
  }

  @Test
  public void testPartial2() {
    doTest();
  }

  @Test
  public void testPartial3() {
    doTest();
  }

  @Test
  public void testPartial4() {
    doTest();
  }

  @Test
  public void testClosure1() {
    doTest();
  }

  @Test
  public void testClosure2() {
    doTest();
  }

  @Test
  public void testClosure3() {
    doTest();
  }

  @Test
  public void testAnonymousClass1() {
    doTest();
  }

  @Test
  public void testRegexInCommandArg1() {
    doTest();
  }

  @Test
  public void testRegexInCommandArg2() {
    doTest();
  }

  @Test
  public void testRegexInCommandArg3() {
    doTest();
  }

  @Test
  public void testRegexInCommandArg4() {
    doTest();
  }

  @Test
  public void testRegexInCommandArg5() {
    doTest();
  }

  @Test
  public void testUndefinedVarInline() {
    doTest();
  }

  @Test
  public void testImplicitCast1() {
    doTest();
  }

  @Test
  public void testImplicitCast2() {
    doTest();
  }

  protected void doFieldTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    InlineMethodTest.doInlineTest(myFixture, myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test", new GroovyInlineHandler());
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean inlineDef) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    String fileText = data.get(0);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    EdtTestUtil.runInEdtAndWait(() -> {
      final Editor editor = myFixture.getEditor();
      final PsiFile file = myFixture.getFile();
      setIndentationToNode(file.getNode());

      int startOffset = editor.getSelectionModel().getSelectionStart();
      int endOffset = editor.getSelectionModel().getSelectionEnd();
      editor.getCaretModel().moveToOffset(endOffset);

      GroovyPsiElement selectedArea = PsiImplUtil.findElementInRange(file, startOffset, endOffset, GrReferenceExpression.class);
      if (selectedArea == null) {
        PsiElement identifier = PsiImplUtil.findElementInRange(file, startOffset, endOffset, PsiElement.class);
        if (identifier != null) {
          assertTrue(identifier.getParent() instanceof GrVariable, "Selected area doesn't point to var");
          selectedArea = (GroovyPsiElement)identifier.getParent();
        }
      }

      assertNotNull(selectedArea, "Selected area reference points to nothing");
      PsiElement element = selectedArea instanceof GrExpression ? selectedArea.getReference().resolve() : selectedArea;
      assertNotNull(element, "Cannot resolve selected reference expression");

      try {
        if (!inlineDef) {
          performInline(myFixture.getProject(), editor);
        }
        else {
          performDefInline(myFixture.getProject(), editor);
        }

        editor.getSelectionModel().removeSelection();
        myFixture.checkResult(data.get(1), true);
      }
      catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        assertEquals(data.get(1), "FAIL: " + e.getMessage());
      }
    });
  }

  public static void performInline(Project project, Editor editor) {
    PsiElement element =
      TargetElementUtil.findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(GrVariable.class, element);

    GroovyInlineLocalHandler.invoke(project, editor, (GrVariable)element);
  }

  public static void performDefInline(Project project, Editor editor) {
    PsiReference reference = TargetElementUtil.findReference(editor);
    assertTrue(reference instanceof PsiReferenceExpression);
    final PsiElement local = reference.resolve();
    assertTrue(local instanceof PsiLocalVariable);

    GroovyInlineLocalHandler.invoke(project, editor, (GrVariable)local);
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
