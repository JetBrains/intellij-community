// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.intentions.declaration.GrIntroduceLocalVariableIntention;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author siosio
 */
public class IntroduceLocalVariableTest extends GrIntentionTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/introduceLocalVariable/";
  }

  public void testMethodCall1() { doTest(); }

  public void testMethodCall2() { doTest(); }

  public void testMethodCall3() { doTest(); }

  public void testMethodCall4() { doTest(); }

  public void testConstructor() { doTest(); }

  public void testClosure1() { doTest(); }

  public void testClosure2() { doTest(); }

  protected void doTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    IntentionAction intention = myFixture.getAvailableIntention("Introduce local variable");
    if (intention != null) {
      new MockGrIntroduceLocalVariableIntention().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    }
    myFixture.checkResultByFile(getTestName(false) + "-after.groovy");
  }

  public static class MockGrIntroduceLocalVariableIntention extends GrIntroduceLocalVariableIntention {
    @Override
    protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
      setSelection(editor, getTargetExpression(element));
      MockSettings settings = new MockSettings(false, "varName", null, false);
      new MockGrIntroduceVariableHandler(settings).invoke(project, editor, element.getContainingFile(), null);
    }
  }
}
