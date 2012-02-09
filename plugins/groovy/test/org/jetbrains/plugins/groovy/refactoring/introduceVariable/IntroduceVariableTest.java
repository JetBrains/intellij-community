/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class IntroduceVariableTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/introduceVariable/";
  }

  public void testAbs() throws Throwable { doTest(); }
  public void testCall1() throws Throwable { doTest(); }
  public void testCall2() throws Throwable { doTest(); }
  public void testCall3() throws Throwable { doTest(); }
  public void testClos1() throws Throwable { doTest(); }
  public void testClos2() throws Throwable { doTest(); }
  public void testClos3() throws Throwable { doTest(); }
  public void testClos4() throws Throwable { doTest(); }
  public void testF2() throws Throwable { doTest(); }
  public void testField1() throws Throwable { doTest(); }
  public void testFirst() throws Throwable { doTest(); }
  public void testIf1() throws Throwable { doTest(); }
  public void testIf2() throws Throwable { doTest(); }
  public void testLocal1() throws Throwable { doTest(); }
  public void testLoop1() throws Throwable { doTest(); }
  public void testLoop2() throws Throwable { doTest(); }
  public void testLoop3() throws Throwable { doTest(); }
  public void testLoop4() throws Throwable { doTest(); }
  public void testLoop5() throws Throwable { doTest(); }
  public void testLoop6() throws Throwable { doTest(); }
  public void testLoop7() throws Throwable { doTest(); }
  public void testLoop8() throws Throwable { doTest(); }
  public void testInCase() {doTest();}

  public void testDuplicatesInsideIf() throws Throwable { doTest(); }
  public void testFromGString() throws Throwable { doTest(); }

  public void testCharArray() {doTest(true);}
  
  public void testCallableProperty() {doTest();}

  protected static final String ALL_MARKER = "<all>";

  protected boolean replaceAllOccurences = false;

  private String processFile(String fileText, boolean explicitType) {
    String result;
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    if (startOffset < 0) {
      startOffset = fileText.indexOf(ALL_MARKER);
      replaceAllOccurences = true;
      fileText = removeAllMarker(fileText);
    } else {
      replaceAllOccurences = false;
      fileText = TestUtils.removeBeginMarker(fileText);
    }
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    Editor myEditor = myFixture.getEditor();

    myEditor.getSelectionModel().setSelection(startOffset, endOffset);

    // gathering data for introduce variable
    final GrIntroduceVariableHandler introduceVariableHandler = new GrIntroduceVariableHandler();

    GrExpression selectedExpr = GrIntroduceHandlerBase.findExpression(((GroovyFileBase)myFixture.getFile()), startOffset, endOffset);

    assertNotNull("Selected expression reference points to null", selectedExpr);

    final PsiElement tempContainer = GroovyRefactoringUtil.getEnclosingContainer(selectedExpr);
    assertTrue(tempContainer instanceof GroovyPsiElement);

    PsiElement[] occurences = GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(selectedExpr, false), tempContainer);
    final String varName = "preved";
    final PsiType varType;
    if (explicitType) {
      varType = selectedExpr.getType();
    }
    else {
      varType = null;
    }

    final GrIntroduceContext context = new GrIntroduceContextImpl(getProject(), myEditor, selectedExpr, null, occurences, tempContainer);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        introduceVariableHandler.runRefactoring(context, new GroovyIntroduceVariableSettings() {
          @Override
          public boolean isDeclareFinal() {
            return false;
          }

          @Nullable
          @Override
          public PsiType getSelectedType() {
            return varType;
          }

          @Override
          public String getName() {
            return varName;
          }

          @Override
          public boolean replaceAllOccurrences() {
            return replaceAllOccurences;
          }
        });
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      }
    });


    result = myEditor.getDocument().getText();
    int caretOffset = myEditor.getCaretModel().getOffset();
    result = result.substring(0, caretOffset) + TestUtils.CARET_MARKER + result.substring(caretOffset);

    return result;
  }

  public void doTest(boolean explicitType) {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    assertEquals(data.get(1).trim(), processFile(data.get(0), explicitType).trim());
  }

  public void doTest() {
    doTest(false);
  }

  protected static String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER);
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length());
  }

}
