// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.modplan.ModPlan;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanCommandAction;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring;
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.RegistryKey;
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tests of the safe delete with Groovy code, run with the flow on the ModPlan engine: the Java provider owns the Groovy declarations,
 * and the Groovy delegate of Java gives the edits of the Groovy usages. A test that fails here is a difference from the legacy flow.
 */
@RegistryKey(key = "refactoring.safeDelete.modPlan", value = "true")
public class SafeDeleteJavaOnModPlanTest extends SafeDeleteJavaTest {
  @BeforeEach
  public void failOnLegacyFlow() {
    ModPlanSafeDeleteFlow.INSTANCE.failOnLegacyInTests(testDisposable);
  }

  @Test
  public void testUnusedGroovyMethodIsDeleted() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile groovyFile = myFixture.configureByText("A.groovy", """
      class A {
        void fo<caret>o() {}
        void bar() {}
      }
      """);

    EdtTestUtil.runInEdtAndWait(() -> {
      assertTrue(deleteOnModPlan(myFixture.getElementAtCaret()));

      String text = groovyFile.getText();
      assertFalse(text.contains("foo"), text);
      assertTrue(text.contains("void bar() {}"), text);
    });
  }

  /** The fix of an unused Groovy method is the safe delete on the ModPlan engine, and it deletes the method. */
  @Test
  public void testFixOfUnusedGroovyMethodDeletesIt() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspectionBase(true));
    PsiFile groovyFile = myFixture.configureByText("A.groovy", """
      class A {
        void fo<caret>o() {}
        void bar() {}
      }
      """);

    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction fix = myFixture.findSingleIntention("Safe delete");
      assertInstanceOf(ModPlanCommandAction.class, IntentionActionDelegate.unwrap(fix).asModCommandAction());
      myFixture.launchAction(fix);

      String text = groovyFile.getText();
      assertFalse(text.contains("foo"), text);
      assertTrue(text.contains("void bar() {}"), text);
    });
  }

  /** As in the legacy flow, Groovy offers no safe delete of a parameter, see {@code GroovyRefactoringSupportProvider}. */
  @Test
  public void testParameterOfAGroovyMethodIsNotApplicable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.groovy", """
      class A {
        void foo(int ba<caret>r) {}
      }
      """);

    EdtTestUtil.runInEdtAndWait(() -> {
      ModPlan plan = planOf(myFixture.getElementAtCaret());

      assertTrue(plan instanceof ModPlan.RefusedPlan refusedPlan &&
                 refusedPlan.getReason() == ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, String.valueOf(plan));
    });
  }

  @Test
  public void testGroovyMethodUsedInJavaIsABrokenConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    EdtTestUtil.runInEdtAndWait(() -> {
      ModPlan plan = planOf(myFixture.getElementAtCaret());

      assertTrue(plan instanceof ModPlan.AcceptedPlan acceptedPlan && acceptedPlan.getConflicts().hasBroken(), String.valueOf(plan));
    });
  }

  private ModPlan planOf(PsiElement element) {
    Project project = codeInsightFixture.get().getProject();
    ModPlanRequest request = ReadAction.computeBlocking(() -> new ModPlanRequest(project, List.of(SymbolPointer.of(element)), ModPlanOptionValues.EMPTY));
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(
      () -> ReadAction.computeBlocking(() -> ModPlanSafeDeleteRefactoring.getInstance().plan(request))));
  }

  /** @return whether the flow on the ModPlan engine handled the deletion, which is false if the legacy flow had to run */
  private boolean deleteOnModPlan(PsiElement element) {
    return ModPlanSafeDeleteFlow.INSTANCE.start(codeInsightFixture.get().getProject(), new PsiElement[]{element}, false, true, null, null);
  }
}
