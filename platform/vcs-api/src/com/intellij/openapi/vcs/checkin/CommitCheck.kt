// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence

/**
 * Represents some check for the commit to detect specific problems.
 * E.g. if there are code errors introduced by the commit changes.
 *
 * [CheckinHandler]-s which need to run some asynchronous computation before commit should implement [CommitCheck].
 * Instead of creating separate modal task in synchronous [CheckinHandler.beforeCheckin] implementation.
 *
 * Implement [com.intellij.openapi.project.DumbAware] to allow running commit check in dumb mode.
 *
 * Note that [CommitCheck] API is only supported in Commit Tool Window.
 */
@ApiStatus.Experimental
interface CommitCheck<T : CommitProblem> {
  /**
   * Indicates if commit check should be run for the commit.
   * E.g. if corresponding option is enabled in settings.
   *
   * @return `true` if commit check should be run for the commit and `false` otherwise
   */
  @RequiresEdt
  fun isEnabled(): Boolean

  /**
   * Runs commit check and returns found commit problem if any.
   *
   * @param indicator indicator to report running progress to
   * @return commit problem found by the commit check or `null` if no problems found
   */
  @RequiresEdt
  suspend fun runCheck(indicator: ProgressIndicator): T?

  /**
   * Shows details of the given commit problem.
   * E.g. navigates to the tool window with problem details.
   *
   * @param problem commit problem to show details for
   */
  @RequiresEdt
  fun showDetails(problem: T)
}

/**
 * Represents some problem found in the commit.
 * E.g. code errors introduced by the commit changes.
 */
@ApiStatus.Experimental
interface CommitProblem {
  /**
   * Short problem description to show to the user.
   * Problem details should be provided by the [CommitCheck.showDetails] logic.
   */
  @get:Nls(capitalization = Sentence)
  val text: String
}