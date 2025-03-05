// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence
import java.io.IOException

/**
 * Represents some check or code modification that is performed when changes are committed into VCS.
 * E.g. to enforce the code style or to check if errors are introduced by the committed changes.
 *
 * This interface supersedes [CheckinHandler.beforeCheckin] method,
 * and will be used instead if [CheckinHandler] implements [CommitCheck].
 * This interface is better suitable for 'non-modal' commit mode,
 * as it allows performing operations without modal progress and modal error dialogs.
 *
 * Implement [com.intellij.openapi.project.DumbAware] to allow running commit check in dumb mode.
 */
interface CommitCheck : PossiblyDumbAware {
  fun getExecutionOrder(): ExecutionOrder

  /**
   * Indicates if commit check should be run for the commit.
   * E.g. if the corresponding option is enabled in settings.
   * See [CheckinHandler.getBeforeCheckinConfigurationPanel] and [CheckinHandler.getBeforeCheckinSettings].
   *
   * @return `true` if commit check should be run for the commit and `false` otherwise
   */
  @RequiresEdt
  fun isEnabled(): Boolean

  /**
   * Runs commit check and returns the found commit problem if any.
   *
   * Method is executed with [com.intellij.openapi.application.EDT] dispatcher.
   * Consider using explicit context (e.g. [kotlinx.coroutines.Dispatchers.Default]) for potentially long operations,
   * that can be performed on pooled thread.
   *
   * Use [com.intellij.openapi.progress.progressStep] to report progress state.
   *
   * @return a commit problem found by the commit check or `null` if no problems found
   */
  @RequiresEdt
  suspend fun runCheck(commitInfo: CommitInfo): CommitProblem?

  enum class ExecutionOrder {
    /**
     * Checks to be performed first.
     */
    EARLY,

    /**
     * Checks that can modify content about to be committed (code cleanups, formatters, etc).
     */
    MODIFICATION,

    /**
     * Checks to be performed after all modifications are finished.
     */
    LATE,

    /**
     * Slow checks that can be performed in background after the actual commit.
     * These can be handled as [LATE] checks in some situations (ex: [git4idea.checkin.GitCommitAndPushExecutor]).
     *
     * @see com.intellij.vcs.commit.isPostCommitCheck
     */
    POST_COMMIT
  }
}

/**
 * Represents some problem found in the commit.
 *
 * @see CommitProblemWithDetails
 */
interface CommitProblem {
  /**
   * Short problem description to show to the user.
   *
   * HTML markup is not supported, use [CommitProblemWithDetails].
   */
  @get:Nls(capitalization = Sentence)
  val text: String

  /**
   * Suggest a solution for the found problem in modal commit mode, if needed.
   * Typically, this is a `Problem found, commit anyway? [Yes/No]` message dialog.
   *
   * Method is not called for non-modal commit modes.
   */
  @RequiresEdt
  fun showModalSolution(project: Project, commitInfo: CommitInfo): CheckinHandler.ReturnResult {
    if (this is CommitProblemWithDetails) {
      val commit = MessageDialogBuilder.yesNoCancel(VcsBundle.message("checkin.commit.checks.failed"), text)
        .yesText(StringUtil.toTitleCase(showDetailsAction))
        .noText(commitInfo.commitActionText)
        .cancelText(VcsBundle.message("checkin.commit.checks.failed.cancel.button"))
        .show(project)
      when (commit) {
        Messages.YES -> { // review
          this.showDetails(project)
          return CheckinHandler.ReturnResult.CLOSE_WINDOW
        }
        Messages.NO -> return CheckinHandler.ReturnResult.COMMIT // commit anyway
        else -> return CheckinHandler.ReturnResult.CANCEL // abort commit
      }
    }
    else {
      val commit = MessageDialogBuilder.yesNo(VcsBundle.message("checkin.commit.checks.failed"), text)
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
      val pluginUtil = PluginUtil.getInstance()
      val pluginName = pluginUtil.findPluginId(e)?.let { pluginId -> pluginUtil.findPluginName(pluginId) }

      var message = when {
        pluginName != null -> VcsBundle.message("before.checkin.error.in.plugin", pluginName)
        else -> VcsBundle.message("before.checkin.error.internal")
      }

      val ioException = ExceptionUtil.findCause(e, IOException::class.java)
      if (ioException != null) {
        message += ". " + ioException.message
      }

      return TextCommitProblem(message)
    }
  }
}

/**
 * Allows to show a link near the error, that can be used to show more detailed explanation or propose a quick fix for the problem.
 */
interface CommitProblemWithDetails : CommitProblem {
  /**
   * If null, the whole [CommitProblem.text] will become a link.
   */
  val showDetailsLink: @NlsContexts.LinkLabel String? get() = null

  val showDetailsAction: @NlsContexts.NotificationContent String

  /**
   * Allows showing details for the problem (ex: by opening a toolwindow tab with a list of failed inspections).
   * Modal dialog will be closed after this call if it is shown.
   */
  @RequiresEdt
  fun showDetails(project: Project)
}

class TextCommitProblem(override val text: String) : CommitProblem

interface CommitInfo {
  val commitContext: CommitContext

  /**
   * Whether commit will be performed using [com.intellij.openapi.vcs.changes.CommitSession.VCS_COMMIT] aka [CheckinEnvironment].
   */
  val isVcsCommit: Boolean
  val executor: CommitExecutor?

  val committedChanges: List<Change>
  val affectedVcses: List<AbstractVcs>
  val commitMessage: @Nls String

  /**
   * Commit action name, without ellipsis. May contain mnemonic. Ex: 'Amend Comm&it'.
   */
  val commitActionText: @Nls String
}

val CommitInfo.committedVirtualFiles: List<VirtualFile> get() = ChangesUtil.iterateFiles(committedChanges).toList()
