// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.util.TextRange
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor

/**
 * Applies [ready] as a legacy refactoring does in a unit test.
 * A conflict throws [BaseRefactoringProcessor.ConflictsInTestsException], unless the test ignores the conflicts; a conflict of a non-code
 * usage does not throw.
 * An execution with a result other than [ModPlanExecutionResult.Applied] throws an [AssertionError] with the message `not applied`.
 * Otherwise it writes the plan.
 */
internal fun applyAsLegacyTest(ready: ModPlan.AcceptedPlan) {
  if (ready.conflicts.needsConfirmation() && !BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) {
    throw BaseRefactoringProcessor.ConflictsInTestsException(
      ready.conflicts.conflicts
        .filter { it.kind != ModPlanConflictKind.NON_CODE_USAGE && it.kind != ModPlanConflictKind.CHANGED_NON_CODE_USAGE }
        .flatMap { it.messages })
  }
  val result = ModPlanExecutor.execute(ready.execution, true, ready.writeEvents)
  if (result !is ModPlanExecutionResult.Applied) throw AssertionError("not applied: $result")
}

/** @return the file and the text of the place where [plan] refuses, for the message of a failed test */
internal fun placeOf(plan: ModPlan.RefusedPlan): String {
  val location = plan.location ?: return "no place"
  val range = location.range ?: return "${location.virtualFile.name}, a gone range"
  val text = location.containingFile?.text?.let { TextRange.create(range).substring(it) }
  return "${location.virtualFile.name} $range '$text'"
}