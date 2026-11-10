/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyVariableValidator;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class IntroduceVariableValidatorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/introduceVariableValidator/");
  }

  @Test
  public void testAll1() {
    doTest();
  }
  @Test
  public void testAll2() {
    doTest();
  }
  @Test
  public void testClass1() {
    doTest();
  }
  @Test
  public void testClass2() {
    doTest();
  }
  @Test
  public void testE() {
    doTest();
  }
  @Test
  public void testFile1() {
    doTest();
  }
  @Test
  public void testFile2() {
    doTest();
  }
  @Test
  public void testLoop1() {
    doTest();
  }
  @Test
  public void testLoop2() {
    doTest();
  }
  @Test
  public void testLoop3() {
    doTest();
  }
  @Test
  public void testSimple() {
    doTest();
  }

  protected static final String ALL_MARKER = "<all>";
  protected boolean replaceAllOccurences = false;

  private String processFile(String fileText) throws IncorrectOperationException, InvalidDataException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    if (startOffset < 0) {
      startOffset = fileText.indexOf(ALL_MARKER);
      replaceAllOccurences = true;
      fileText = IntroduceVariableTest.removeAllMarker(fileText);
    } else {
      replaceAllOccurences = false;
      fileText = TestUtils.removeBeginMarker(fileText);
    }
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    Editor myEditor = myFixture.getEditor();

    int selectionStart = startOffset;
    EdtTestUtil.runInEdtAndWait(() -> myEditor.getSelectionModel().setSelection(selectionStart, endOffset));

    return ReadAction.computeBlocking(() -> {
      GrExpression selectedExpr = PsiImplUtil.findElementInRange(myFixture.getFile(), selectionStart, endOffset, GrExpression.class);

      assertNotNull(selectedExpr, "Selected expression reference points to null");

      final PsiElement tempContainer = GrIntroduceHandlerBase.getEnclosingContainer(selectedExpr);
      assertTrue(tempContainer instanceof GroovyPsiElement);

      PsiElement[] occurences = GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(selectedExpr, false), tempContainer);
      String varName = "preved";
      GroovyVariableValidator validator =
        new GroovyVariableValidator(new GrIntroduceContextImpl(myFixture.getProject(), myEditor, selectedExpr, null, null, occurences, tempContainer));
      return validator.isOKTest(varName, replaceAllOccurences);
    });
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    assertEquals(StringUtil.trimEnd(data.get(1), "\n"), processFile(data.get(0)));
  }

}
