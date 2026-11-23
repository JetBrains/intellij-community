// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable

/** Runs the tests of the legacy rename of a language on the ModPlan engine. */
@TestOnly
object ModPlanRenameTestSupport {
  /**
   * Renames [target] on the ModPlan engine as the legacy rename does in a unit test: a conflict throws
   * [BaseRefactoringProcessor.ConflictsInTestsException], unless the test ignores the conflicts, and then the rename is written. The plan is
   * made on a background thread, as the MCP tool makes it.
   *
   * @param options the answers of the questions; the provider may substitute [target], as the processor of the legacy test does
   */
  @JvmStatic
  fun renameOnModPlan(project: Project, target: PsiElement, newName: String, options: ModPlanOptionValues) {
    val ready = when (val plan = planOnModPlan(project, target, newName, options)) {
      // The legacy rename shows the message of a general reason before it starts, as an error hint, which a unit test throws.
      is ModPlan.RefusedPlan -> throw runReadActionBlocking { PsiElementRenameHandler.getRenameErrorMessage(project, null, target, true) }?.let {
        CommonRefactoringUtil.RefactoringErrorHintException(RefactoringBundle.getCannotRefactorMessage(it))
      } ?: AssertionError("refused: ${plan.reason} ${plan.message.orEmpty()} at ${runReadActionBlocking { placeOf(plan) }}")
      is ModPlan.AcceptedPlan -> plan
    }
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
  private fun placeOf(plan: ModPlan.RefusedPlan): String {
    val location = plan.location ?: return "no place"
    val range = location.range ?: return "${location.virtualFile.name}, a gone range"
    val text = location.containingFile?.text?.let { TextRange.create(range).substring(it) }
    return "${location.virtualFile.name} $range '$text'"
  }

  /**
   * @param options the answers of the questions; the provider may substitute [target]
   * @return the plan of the rename of [target], made on a background thread under a progress indicator, as the MCP tool makes it;
   * nothing is written
   */
  @JvmStatic
  fun planOnModPlan(project: Project, target: PsiElement, newName: String, options: ModPlanOptionValues): ModPlan =
    planOnModPlan(project, target, newName, substitute = true, options)

  /**
   * @param substitute whether the provider may substitute [target], as for a caller with no UI of its own
   * @return the plan of the rename of [target], see [planOnModPlan]
   */
  @JvmStatic
  fun planOnModPlan(project: Project, target: PsiElement, newName: String, substitute: Boolean, options: ModPlanOptionValues): ModPlan {
    val request = runReadActionBlocking { ModPlanRenameRefactoring.request(project, target, newName, substitute, options) }
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
      ProgressManager.getInstance().runProcess(Computable {
        runReadActionBlocking { ModPlanRenameRefactoring.getInstance().plan(request) }
      }, EmptyProgressIndicator())
    }))
  }

  /** @return [options] with every automatic renamer on, as a legacy test that adds every factory */
  @JvmStatic
  fun withEveryRenamer(options: ModPlanOptionValues): ModPlanOptionValues =
    ModPlanRenameOptions.withRenamers(options, ModPlanRenameOptions.renamerIds { true })

  /** @return the options that turn on the automatic renamers of the factories of [factoryClasses] alone */
  @JvmStatic
  fun withRenamersOf(vararg factoryClasses: Class<out AutomaticRenamerFactory>): ModPlanOptionValues =
    ModPlanRenameOptions.withRenamers(ModPlanOptionValues.EMPTY, factoryClasses.map { renamerIdOf(it) })

  /** @return the [AutomaticRenamerFactory.getOptionId] of the registered factory of [factoryClass] */
  @JvmStatic
  fun renamerIdOf(factoryClass: Class<out AutomaticRenamerFactory>): String =
    @Suppress("SplitModeApiUsage")
    AutomaticRenamerFactory.EP_NAME.findExtensionOrFail(factoryClass).optionId!!
}
