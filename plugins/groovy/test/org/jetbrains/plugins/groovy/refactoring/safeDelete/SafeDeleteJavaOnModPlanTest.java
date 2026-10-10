// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.modplan.ModPlan;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanCommandAction;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring;
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;

import java.util.List;

/**
 * The tests of the safe delete with Groovy code, run with the flow on the ModPlan engine: the Java provider owns the Groovy declarations,
 * and the Groovy delegate of Java gives the edits of the Groovy usages. A test that fails here is a difference from the legacy flow.
 */
public class SafeDeleteJavaOnModPlanTest extends SafeDeleteJavaTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get("refactoring.safeDelete.modPlan").setValue(true, getTestRootDisposable());
    ModPlanSafeDeleteFlow.INSTANCE.failOnLegacyInTests(getTestRootDisposable());
  }

  public void testUnusedGroovyMethodIsDeleted() {
    PsiFile groovyFile = myFixture.configureByText("A.groovy", """
      class A {
        void fo<caret>o() {}
        void bar() {}
      }
      """);

    assertTrue(deleteOnModPlan(myFixture.getElementAtCaret()));

    String text = groovyFile.getText();
    assertFalse(text, text.contains("foo"));
    assertTrue(text, text.contains("void bar() {}"));
  }

  /** The fix of an unused Groovy method is the safe delete on the ModPlan engine, and it deletes the method. */
  public void testFixOfUnusedGroovyMethodDeletesIt() {
    myFixture.enableInspections(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspectionBase(true));
    PsiFile groovyFile = myFixture.configureByText("A.groovy", """
      class A {
        void fo<caret>o() {}
        void bar() {}
      }
      """);

    IntentionAction fix = myFixture.findSingleIntention("Safe delete");
    assertInstanceOf(IntentionActionDelegate.unwrap(fix).asModCommandAction(), ModPlanCommandAction.class);
    myFixture.launchAction(fix);

    String text = groovyFile.getText();
    assertFalse(text, text.contains("foo"));
    assertTrue(text, text.contains("void bar() {}"));
  }

  /** As in the legacy flow, Groovy offers no safe delete of a parameter, see {@code GroovyRefactoringSupportProvider}. */
  public void testParameterOfAGroovyMethodIsNotApplicable() {
    myFixture.configureByText("A.groovy", """
      class A {
        void foo(int ba<caret>r) {}
      }
      """);

    ModPlan plan = planOf(myFixture.getElementAtCaret());

    assertTrue(String.valueOf(plan), plan instanceof ModPlan.RefusedPlan refusedPlan &&
                                     refusedPlan.getReason() == ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE);
  }

  public void testGroovyMethodUsedInJavaIsABrokenConflict() {
    myFixture.configureByText("A.groovy", """
      class A {
        void fo<caret>o() {}
      }
      """);
    myFixture.addFileToProject("B.java", """
      class B {
        void use() { new A().foo(); }
      }
      """);

    ModPlan plan = planOf(myFixture.getElementAtCaret());

    assertTrue(String.valueOf(plan), plan instanceof ModPlan.AcceptedPlan acceptedPlan && acceptedPlan.getConflicts().hasBroken());
  }

  private ModPlan planOf(PsiElement element) {
    ModPlanRequest request = ReadAction.computeBlocking(() -> new ModPlanRequest(getProject(), List.of(SymbolPointer.of(element)), ModPlanOptionValues.EMPTY));
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(
      () -> ReadAction.computeBlocking(() -> ModPlanSafeDeleteRefactoring.getInstance().plan(request))));
  }

  /** @return whether the flow on the ModPlan engine handled the deletion, which is false if the legacy flow had to run */
  private boolean deleteOnModPlan(PsiElement element) {
    return ModPlanSafeDeleteFlow.INSTANCE.start(getProject(), new PsiElement[]{element}, false, true, null, null);
  }
}
