// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.jetbrains.plugins.groovy.intentions.declaration.GrIntroduceLocalVariableIntention;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author siosio
 */
@TestApplication
public class IntroduceLocalVariableTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, null, TestUtils.getAbsoluteTestDataPath() + "intentions/introduceLocalVariable/");

  @Test
  public void testMethodCall1() { doTest(); }

  @Test
  public void testMethodCall2() { doTest(); }

  @Test
  public void testMethodCall3() { doTest(); }

  @Test
  public void testMethodCall4() { doTest(); }

  @Test
  public void testConstructor() { doTest(); }

  @Test
  public void testClosure1() { doTest(); }

  @Test
  public void testClosure2() { doTest(); }

  @Test
  public void testVar() {
    doTest("<caret>1", "var x = 1", "var", false);
  }

  @Test
  public void testFinalVar() {
    doTest("<caret>1", "final var x = 1", "var", true);
  }

  @Test
  public void testVal() {
    doTest("<caret>true", "val x = true", "val", true);
  }

  @Test
  public void testFinal() {
    doTest("<caret>0.0", "final x = 0.0", "final", true);
  }

  @Test
  public void testDef() {
    doTest("<caret>0", "final def x = 0", "def", true);
  }

  private void doTest(String before, String after, String typeString, boolean makeFinal) {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.configureByText(myFixture.getTestName(true) + ".groovy", before);
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClassType type = GroovyPsiManager.getInstance(myFixture.getProject())
        .createTypeByFQClassName(typeString, myFixture.getFile().getResolveScope());
      MockSettings settings = new MockSettings(makeFinal, "x", type, false);
      new MockGrIntroduceVariableHandler(settings).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResult(after);
    });
  }

  protected void doTest() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction intention = myFixture.getAvailableIntention("Introduce local variable");
      if (intention != null) {
        new MockGrIntroduceLocalVariableIntention().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      }
      myFixture.checkResultByFile(myFixture.getTestName(false) + "-after.groovy");
    });
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
