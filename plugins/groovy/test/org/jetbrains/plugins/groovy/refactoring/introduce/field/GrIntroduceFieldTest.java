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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.*;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceField/";
  }

  public void testSimple() {
    doTest(false, false, false, CUR_METHOD, false, null);
  }

  public void testDeclareFinal() {
    doTest(false, false, true, FIELD_DECLARATION, false, null);
  }

  public void testCreateConstructor() {
    doTest(false, false, true, CONSTRUCTOR, true, null);
  }

  public void testManyConstructors() {
    doTest(false, false, true, CONSTRUCTOR, true, null);
  }

  public void testDontReplaceStaticOccurrences() {
    doTest(false, false, true, FIELD_DECLARATION, true, null);
  }

  public void testQualifyUsages() {
    doTest(false, false, true, FIELD_DECLARATION, true, null);
  }

  public void testReplaceLocalVar() {
    doTest(false, true, false, CUR_METHOD, true, null);
  }

  public void testIntroduceLocalVarByDeclaration() {
    doTest(false, true, false, FIELD_DECLARATION, true, null);
  }

  private void doTest(final boolean isStatic,
                      final boolean removeLocal,
                      final boolean declareFinal,
                      final GrIntroduceFieldSettings.Init initIn,
                      final boolean replaceAll,
                      final String selectedType) {
    myFixture.configureByFile(getTestName(false) + ".groovy");

    final PsiType type =
      selectedType == null ? null : JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(selectedType, myFixture.getFile());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final IntroduceFieldTestHandler handler =
          new IntroduceFieldTestHandler(isStatic, removeLocal, declareFinal, initIn, replaceAll, type);
        handler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), null);
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      }
    });

    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }
}
