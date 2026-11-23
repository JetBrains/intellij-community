// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.testFramework.SafeDeleteTestSupport;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * The tests of the safe delete with Groovy code, run through the headless safe delete on the ModPlan engine, as the MCP tool runs it. A
 * test that fails here is a difference of the headless safe delete from the legacy one.
 * <p>
 * The answers are the ones of the legacy unit-test mode, as in {@link SafeDeleteJavaOnModPlanTest}.
 */
public class SafeDeleteJavaHeadlessTest extends SafeDeleteJavaTest {
  @Override
  protected @NotNull Map<ModPlanOption<?>, Object> defaultAnswers() {
    return SafeDeleteJavaOnModPlanTest.legacyUnitTestAnswers();
  }

  @Override
  protected void performSafeDelete(@NotNull List<PsiElement> elements,
                                   @NotNull Map<ModPlanOption<?>, Object> answers,
                                   @NotNull SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder) {
    SafeDeleteTestSupport.headlessSafeDelete(codeInsightFixture.get().getProject(), elements,
                                             SafeDeleteTestSupport.withAll(ModPlanOptionValues.EMPTY, answers),
                                             BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore(), nonCodeUsageRecorder);
  }
}
