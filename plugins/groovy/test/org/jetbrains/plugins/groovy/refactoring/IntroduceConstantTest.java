/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.constant.GrIntroduceConstantHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.constant.GrIntroduceConstantSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class IntroduceConstantTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/introduceConstant/";
  }

  public void testSimple() {
    doTest();
  }

  public void testReplaceAllOccurences() {
    doTest();
  }

  public void testEscalateVisibility() {
    doTest("Other", true, false, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testInsertInEnum() {
    doTest("Planet", false, false, GrModifier.PROTECTED);
  }

  public void testInsertInInterface() {
    doTest("MyInterface", false, false, GrModifier.PROTECTED);
  }

  private void doTest() {
    doTest(null, true, true, GrModifier.PUBLIC);
  }

  private void doTest(@Nullable String targetClassName, boolean replaceAllOccurences, boolean useExplicitType, String modifier) {
    myFixture.configureByFile(getTestName(false) + ".groovy");



    final GrIntroduceConstantHandler handler = new GrIntroduceConstantHandler();
    final Editor editor = myFixture.getEditor();

    final GrExpression expression = findExpression(editor);

    final GrIntroduceContext context = handler.getContext(getProject(), editor, expression, null);

    PsiClass targetClass;
    if (targetClassName == null) {
      targetClass = GrIntroduceConstantHandler.findContainingClass(context);
    }
    else {
      targetClass = myFixture.findClass(targetClassName);
    }
    assertNotNull("target class is null", targetClass);

    final GrIntroduceConstantSettings settings =
      new MockIntroduceConstantSettings(targetClass, replaceAllOccurences, useExplicitType ? expression.getType() : null, modifier);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        handler.runRefactoring(context, settings);
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      }
    });
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true);
  }

  private GrExpression findExpression(Editor editor) {
    final int start = editor.getSelectionModel().getSelectionStart();
    final int end = editor.getSelectionModel().getSelectionEnd();
    return GrIntroduceHandlerBase.findExpression((GroovyFileBase)myFixture.getFile(), start, end);
  }

  private static class MockIntroduceConstantSettings implements GrIntroduceConstantSettings {
    private PsiClass myTargetClass;
    private boolean myReplaceAllOccurrences;
    private PsiType mySelectedType;
    private String myModifier;

    private MockIntroduceConstantSettings(@NotNull PsiClass targetClass,
                                          boolean replaceAllOccurrences,
                                          @Nullable PsiType selectedType,
                                          String modifier) {
      myTargetClass = targetClass;
      myReplaceAllOccurrences = replaceAllOccurrences;
      mySelectedType = selectedType;
      myModifier = modifier;
    }

    @Override
    public String getVisibilityModifier() {

      return myModifier;
    }

    @Override
    public PsiClass getTargetClass() {
      return myTargetClass;
    }

    @Override
    public String getName() {
      return "CONST";
    }

    @Override
    public boolean replaceAllOccurrences() {
      return myReplaceAllOccurrences;
    }

    @Override
    public PsiType getSelectedType() {
      return mySelectedType;
    }
  }
}
