// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.move.ModPlanMoveRefactoring
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable

/** The entry point that runs the tests of the legacy move of a language on the ModPlan engine. */
@TestOnly
object ModPlanMoveTestSupport {
  /**
   * Moves [elements] into the directory [targetDirectoryUrl] on the ModPlan engine.
   * It makes the plan on a background thread, as the MCP tool does.
   * A refusal throws an [AssertionError].
   * The legacy move has no such refusal: the rule is only for the tests on the ModPlan engine.
   * A conflict throws [com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException], unless the test ignores the conflicts.
   * Then it writes the move.
   *
   * @param targetDirectoryUrl the VFS URL of the target directory
   * @param options            the answers of the questions, such as the search in the comments
   */
  @JvmStatic
  fun moveOnModPlan(project: Project, elements: List<PsiElement>, targetDirectoryUrl: String, options: ModPlanOptionValues) {
    when (val plan = planOnModPlan(project, elements, targetDirectoryUrl, options)) {
      is ModPlan.RefusedPlan -> {
        val place = runReadActionBlocking { placeOf(plan) }
        throw AssertionError("refused: ${plan.reason} ${plan.message.orEmpty()} at $place")
      }
      is ModPlan.AcceptedPlan -> applyAsLegacyTest(plan)
    }
  }

  /**
   * Gives the subjects of a move as a client of the language server sends them: the paths of files.
   * An element that is not a file or a directory, such as a class, gives its file. An element without a file, such as a package, stays.
   * A file comes once.
   */
  @JvmStatic
  fun filesOf(elements: List<PsiElement>): List<PsiElement> =
    elements.map { it as? PsiFileSystemItem ?: it.containingFile ?: it }.distinct()

  /**
   * Makes the plan of the move of [elements] on a background thread under a progress indicator, as the MCP tool does.
   * It writes nothing.
   *
   * @param targetDirectoryUrl the VFS URL of the target directory
   * @param options            the answers of the questions, such as the search in the comments
   * @return the plan of the move
   */
  @JvmStatic
  fun planOnModPlan(project: Project, elements: List<PsiElement>, targetDirectoryUrl: String, options: ModPlanOptionValues): ModPlan {
    val request = runReadActionBlocking { ModPlanMoveRefactoring.request(project, elements, targetDirectoryUrl, options) }
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
      ProgressManager.getInstance().runProcess(Computable {
        runReadActionBlocking { ModPlanMoveRefactoring.getInstance().plan(request) }
      }, EmptyProgressIndicator())
    }))
  }
}