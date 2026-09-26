// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.intentions.declaration.GrIntroduceLocalVariableIntention;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
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

  public void testVar() {
    doTest("<caret>1", "var x = 1", "var", false);
  }

  public void testFinalVar() {
    doTest("<caret>1", "final var x = 1", "var", true);
  }

  public void testVal() {
    doTest("<caret>true", "val x = true", "val", true);
  }

  public void testFinal() {
    doTest("<caret>0.0", "final x = 0.0", "final", true);
  }

  public void testDef() {
    doTest("<caret>0", "final def x = 0", "def", true);
  }

  private void doTest(String before, String after, String typeString, boolean makeFinal) {
    myFixture.configureByText(getTestName(true) + ".groovy", before);
    PsiClassType type = GroovyPsiManager.getInstance(myFixture.getProject())
      .createTypeByFQClassName(typeString, myFixture.getFile().getResolveScope());
    MockSettings settings = new MockSettings(makeFinal, "x", type, false);
    new MockGrIntroduceVariableHandler(settings).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
    myFixture.checkResult(after);
  }

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
