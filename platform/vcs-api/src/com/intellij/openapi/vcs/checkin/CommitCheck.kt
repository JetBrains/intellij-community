// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsBundle
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
interface CommitCheck : PossiblyDumbAware {
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
  suspend fun runCheck(indicator: ProgressIndicator): CommitProblem?
}

/**
 * Represents some problem found in the commit.
 * E.g. code errors introduced by the commit changes.
 */
@ApiStatus.Experimental
interface CommitProblem {
  /**
   * Short problem description to show to the user.
   *
   * Problem details can be provided by implementing [CommitProblemWithDetails] interface.
   */
  @get:Nls(capitalization = Sentence)
  val text: String

  /**
   * Show modal resolution dialog for modal commit mode, if needed.
   */
  @RequiresEdt
  fun showModalSolution(project: Project, commitInfo: CommitInfo): CheckinHandler.ReturnResult {
    if (this is CommitProblemWithDetails) {
      val commit = MessageDialogBuilder.yesNoCancel(VcsBundle.message("checkin.commit.checks.failed"),
                                                    VcsBundle.message("checkin.commit.checks.failed.with.error.message", text))
        .yesText(VcsBundle.message("checkin.commit.checks.failed.review.button"))
        .noText(commitInfo.commitActionText)
        .cancelText(VcsBundle.message("checkin.commit.checks.failed.cancel.button"))
        .show(project)
      when (commit) {
        Messages.YES -> { // review
          this.showDetails(project, commitInfo)
          return CheckinHandler.ReturnResult.CLOSE_WINDOW
        }
        Messages.NO -> return CheckinHandler.ReturnResult.COMMIT // commit anyway
        else -> return CheckinHandler.ReturnResult.CANCEL // abort commit
      }
    }
    else {
      val commit = MessageDialogBuilder.yesNo(VcsBundle.message("checkin.commit.checks.failed"),
                                              VcsBundle.message("checkin.commit.checks.failed.with.error.message", text))
        .yesText(commitInfo.commitActionText)
        .noText(VcsBundle.message("checkin.commit.checks.failed.cancel.button"))
        .ask(project)
      if (commit) {
        return CheckinHandler.ReturnResult.COMMIT
      }
      else {
        return CheckinHandler.ReturnResult.CANCEL
      }
    }
  }

  companion object {
    fun createError(e: Throwable): CommitProblem {
      val err = e.message
      val message = when {
        err.isNullOrBlank() -> VcsBundle.message("before.checkin.error.unknown")
        else -> VcsBundle.message("before.checkin.error.unknown.details", err)
      }
      return TextCommitProblem(message)
    }
  }
}

@ApiStatus.Experimental
interface CommitProblemWithDetails : CommitProblem {
  /**
   * Shows details of the given commit problem.
   * E.g. navigates to the tool window with problem details.
   */
  @RequiresEdt
  fun showDetails(project: Project, commitInfo: CommitInfo)
}

class TextCommitProblem(override val text: String) : CommitProblem

interface CommitInfo {
  /**
   * Commit action name, without mnemonics and ellipsis. Ex: 'Amend Commit'.
   */
  val commitActionText: @Nls String
}
