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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
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

import java.util.List;

/**
 * @author ilyas
 */
public class IntroduceVariableValidatorTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/introduceVariableValidator/";
  }

  public void testAll1() { doTest(); }
  public void testAll2() { doTest(); }
  public void testClass1() { doTest(); }
  public void testClass2() { doTest(); }
  public void testE() { doTest(); }
  public void testFile1() { doTest(); }
  public void testFile2() { doTest(); }
  public void testLoop1() { doTest(); }
  public void testLoop2() { doTest(); }
  public void testLoop3() { doTest(); }
  public void testSimple() { doTest(); }

  protected static final String ALL_MARKER = "<all>";
  protected boolean replaceAllOccurences = false;

  private String processFile(String fileText) throws IncorrectOperationException, InvalidDataException {
    String result = "";
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

    myEditor.getSelectionModel().setSelection(startOffset, endOffset);

    GrExpression selectedExpr = PsiImplUtil.findElementInRange(myFixture.getFile(), startOffset, endOffset, GrExpression.class);

    Assert.assertNotNull("Selected expression reference points to null", selectedExpr);

    final PsiElement tempContainer = GrIntroduceHandlerBase.getEnclosingContainer(selectedExpr);
    Assert.assertTrue(tempContainer instanceof GroovyPsiElement);

    PsiElement[] occurences = GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(selectedExpr, false), tempContainer);
    String varName = "preved";
    GroovyVariableValidator validator =
      new GroovyVariableValidator(new GrIntroduceContextImpl(getProject(), myEditor, selectedExpr, null, null, occurences, tempContainer));
    result = validator.isOKTest(varName, replaceAllOccurences);
    return result;
  }


  public void doTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    assertEquals(StringUtil.trimEnd(data.get(1), "\n"), processFile(data.get(0)));
  }

}
