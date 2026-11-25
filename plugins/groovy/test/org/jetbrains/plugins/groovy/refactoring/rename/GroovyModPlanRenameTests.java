// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.ModPlan;
import com.intellij.refactoring.modplan.ModPlanExecutionResult;
import com.intellij.refactoring.modplan.ModPlanExecutor;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ModPlanRenameTestSupport;
import org.jetbrains.annotations.NotNull;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The rename of Groovy elements on the ModPlan engine in a test, see {@link ModPlanRenameTestSupport}. */
public final class GroovyModPlanRenameTests {
  private GroovyModPlanRenameTests() {
  }

  /** @return the plan of the rename of {@code element}, see {@link ModPlanRenameTestSupport#planOnModPlan} */
  public static @NotNull ModPlan plan(@NotNull PsiElement element, @NotNull String newName) {
    return ModPlanRenameTestSupport.planOnModPlan(element.getProject(), element, newName, ModPlanOptionValues.EMPTY);
  }

  /** Renames {@code element} on the EDT, see {@link ModPlanRenameTestSupport#renameOnModPlan}. */
  public static void rename(@NotNull PsiElement element, @NotNull String newName) {
    EdtTestUtil.runInEdtAndWait(
      () -> ModPlanRenameTestSupport.renameOnModPlan(element.getProject(), element, newName, ModPlanOptionValues.EMPTY));
  }

  /** @return the plan of the rename of {@code element} with no substitution, as for a caller that substitutes the element itself */
  public static @NotNull ModPlan planWithNoSubstitution(@NotNull PsiElement element, @NotNull String newName) {
    return ModPlanRenameTestSupport.planOnModPlan(element.getProject(), element, newName, false, ModPlanOptionValues.EMPTY);
  }

  /** Renames {@code element} with no substitution, see {@link #planWithNoSubstitution}. */
  public static void renameWithNoSubstitution(@NotNull PsiElement element, @NotNull String newName) {
    ModPlan.AcceptedPlan accepted = assertInstanceOf(ModPlan.AcceptedPlan.class, planWithNoSubstitution(element, newName));
    EdtTestUtil.runInEdtAndWait(() -> {
      ModPlanExecutionResult result = ModPlanExecutor.execute(accepted.getExecution(), true, accepted.getWriteEvents());
      assertInstanceOf(ModPlanExecutionResult.Applied.class, result);
    });
  }

  /** Asserts that the provider of {@code providerClass} owns {@code element}: it is the first one that accepts it. */
  public static void assertOwner(@NotNull PsiElement element, @NotNull Class<? extends ModPlanRenameProvider> providerClass) {
    ModPlanRenameProvider owner = ReadAction.computeBlocking(() -> {
      SymbolPointer subject = SymbolPointer.of(element);
      return ModPlanRenameProvider.EP_NAME.findFirstSafe(provider -> provider.isOwner(subject));
    });
    assertNotNull(owner, "no provider owns " + element);
    assertInstanceOf(providerClass, owner);
  }
}
