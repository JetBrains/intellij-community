// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbModeBlockedFunctionalityCollector
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vcs.checkin.*
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class PostCommitChecksHandler(val project: Project) {
  companion object {
    private val LOG = logger<PostCommitChecksHandler>()

    @JvmStatic
    fun getInstance(project: Project): PostCommitChecksHandler = project.service()
  }

  private val postCommitCheckErrorNotifications = SingletonNotificationManager(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.displayId,
                                                                               NotificationType.WARNING)

  private val pendingCommits = mutableListOf<StaticCommitInfo>()
  private var lastCommitProblems: List<CommitProblem>? = null

  private var lastJob: Job? = null

  fun canHandle(commitInfo: CommitInfo): Boolean {
    return commitInfo.affectedVcses.isNotEmpty() &&
           commitInfo.affectedVcses.all { vcs -> vcs.checkinEnvironment?.postCommitChangeConverter != null }
  }

  fun resetPendingCommits() {
    pendingCommits.clear()
    postCommitCheckErrorNotifications.clear()
    lastCommitProblems = null
    lastJob?.cancel()
  }

  @RequiresEdt
  fun startPostCommitChecksTask(commitInfo: StaticCommitInfo, commitChecks: List<CommitCheck>) {
    val previousJob = lastJob
    @OptIn(DelicateCoroutinesApi::class)
    lastJob = GlobalScope.launch(CoroutineName("post commit checks") + Dispatchers.EDT) {
      previousJob?.cancelAndJoin()
      runPostCommitChecks(commitInfo, commitChecks)
    }
  }

  private suspend fun runPostCommitChecks(commitInfo: StaticCommitInfo,
                                          commitChecks: List<CommitCheck>) {
    withBackgroundProgress(project, VcsBundle.message("post.commit.checks.progress.text")) {
      reportSequentialProgress { reporter ->
        val postCommitInfo = reporter.nextStep(20, VcsBundle.message("post.commit.checks.progress.step.collecting.commits.text")) {
          prepareCommitsToCheck(commitInfo)
        }

        reporter.nextStep(100) {
          val problems = runCommitChecks(commitChecks, postCommitInfo)
          if (problems.isEmpty()) {
            LOG.debug("Post-commit checks succeeded")
            pendingCommits.clear()
            postCommitCheckErrorNotifications.clear()
            lastCommitProblems = null
          }
          else {
            postCommitCheckErrorNotifications.clear()
            reportPostCommitChecksFailure(problems)
            lastCommitProblems = problems
          }
        }
      }
    }
  }

  private suspend fun prepareCommitsToCheck(commitInfo: StaticCommitInfo): PostCommitInfo = reportSequentialProgress { reporter ->
    val lastCommitInfos = pendingCommits.toList()
    pendingCommits += commitInfo

    if (lastCommitInfos.isNotEmpty()) {
      val mergedCommitInfo = withContext(Dispatchers.IO) {
        reporter.indeterminateStep {
          coroutineToIndicator {
            mergeCommitInfos(lastCommitInfos, commitInfo)
          }
        }
      }
      if (mergedCommitInfo != null) return mergedCommitInfo

      LOG.debug("Dropping pending commits: ${lastCommitInfos.size}")
      pendingCommits.clear()
      pendingCommits += commitInfo
    }

    return withContext(Dispatchers.IO) {
      reporter.indeterminateStep {
        coroutineToIndicator {
          createPostCommitInfo(commitInfo)
        }
      }
    }
  }

  private suspend fun runCommitChecks(commitChecks: List<CommitCheck>,
                                      postCommitInfo: PostCommitInfo): List<CommitProblem> {
    val problems = mutableListOf<CommitProblem>()

    if (DumbService.isDumb(project)) {
      if (commitChecks.any { !DumbService.isDumbAware(it) }) {
        problems += TextCommitProblem(VcsBundle.message("before.checkin.post.commit.error.dumb.mode"))
        DumbModeBlockedFunctionalityCollector.logFunctionalityBlocked(project, DumbModeBlockedFunctionality.PostCommitCheck)
      }
    }

    problems += commitChecks.mapWithProgress { commitCheck ->
      AbstractCommitWorkflow.runCommitCheck(project, commitCheck, postCommitInfo)
    }.filterNotNull()
    return problems
  }

  @RequiresBackgroundThread
  private fun mergeCommitInfos(lastCommitInfos: List<StaticCommitInfo>, currentCommit: StaticCommitInfo): PostCommitInfo? {
    if (lastCommitInfos.isEmpty()) return null

    val allCommits = lastCommitInfos + currentCommit
    val allVcses = allCommits.flatMap { it.affectedVcses }.toSet()
    val allCommitContexts = allCommits.map { it.commitContext }

    val changeConverters = allVcses
      .mapNotNull { vcs -> vcs.checkinEnvironment?.postCommitChangeConverter }
    if (changeConverters.isEmpty()) {
      LOG.error("Post-commit change converters not found for ${allVcses}")
      return null
    }

    if (changeConverters.any { changeConverter -> !changeConverter.areConsequentCommits(allCommitContexts) }) {
      LOG.debug("Non-consequent commits")
      return null
    }

    val staticChanges = mutableListOf<Change>()
    for (commit in allCommits) {
      staticChanges += collectChangesFor(commit.commitContext, changeConverters) ?: return null
    }
    val zippedChanges = CommittedChangesTreeBrowser.zipChanges(staticChanges)

    return PostCommitInfo(currentCommit, zippedChanges)
  }

  @RequiresBackgroundThread
  private fun createPostCommitInfo(commitInfo: StaticCommitInfo): PostCommitInfo {
    val changeConverters = commitInfo.affectedVcses.mapNotNull { vcs -> vcs.checkinEnvironment?.postCommitChangeConverter }
    if (changeConverters.isEmpty()) LOG.error("Post-commit change converters not found for ${commitInfo.affectedVcses}")

    val staticChanges = collectChangesFor(commitInfo.commitContext, changeConverters)

    return PostCommitInfo(commitInfo, staticChanges ?: commitInfo.committedChanges)
  }

  @RequiresBackgroundThread
  private fun collectChangesFor(commitContext: CommitContext, changeConverters: List<PostCommitChangeConverter>): List<Change>? {
    try {
      val staticChanges = mutableListOf<Change>()
      for (changeConverter in changeConverters) {
        staticChanges += changeConverter.collectChangesAfterCommit(commitContext)
      }
      if (staticChanges.isEmpty()) {
        LOG.warn("Post-commit converters returned empty list of changes")
        return null
      }
      return staticChanges
    }
    catch (e: VcsException) {
      LOG.warn(e)
      return null
    }
  }

  private fun reportPostCommitChecksFailure(problems: List<CommitProblem>) {
    postCommitCheckErrorNotifications.notify(VcsBundle.message("post.commit.checks.failed.notification.title"),
                                             problems.joinToString("<br/>") { it.text },
                                             project) { notification ->
      notification.setDisplayId(VcsNotificationIdsHolder.POST_COMMIT_CHECKS_FAILED)

      for (problem in problems.filterIsInstance<CommitProblemWithDetails>()) {
        notification.addAction(NotificationAction.createSimple(problem.showDetailsAction.dropMnemonic()) {
          CommitSessionCollector.getInstance(project).logCommitProblemViewed(problem, CommitProblemPlace.NOTIFICATION)
          problem.showDetails(project)
        })
      }

      notification.addAction(NotificationAction.createSimple(VcsBundle.message("post.commit.checks.failed.notification.ignore.action")) {
        resetPendingCommits()
      })
    }
  }

  fun createPushStatusNotification(closeDialog: Runnable): JComponent? {
    if (lastJob?.isActive == true) {
      return EditorNotificationPanel(EditorNotificationPanel.Status.Warning)
        .text(VcsBundle.message("post.commit.checks.not.finished.push.dialog.notification.text"))
    }

    val problems = lastCommitProblems
    if (problems != null) {
      val lastCommitInfos = pendingCommits.toList()
      val allVcses = lastCommitInfos.flatMap { it.affectedVcses }.toSet()
      val commitContexts = lastCommitInfos.map { it.commitContext }

      val changeConverters = allVcses
        .mapNotNull { vcs -> vcs.checkinEnvironment?.postCommitChangeConverter }
      if (changeConverters.none { it.isFailureUpToDate(commitContexts) }) return null

      val text = StringUtil.shortenTextWithEllipsis(problems.joinToString(", ") { it.text }, 100, 0)
      val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Error)
        .text(VcsBundle.message("post.commit.checks.failed.push.dialog.notification.text", text))
      for (problem in problems) {
        if (problem is CommitProblemWithDetails) {
          panel.createActionLabel(problem.showDetailsAction.dropMnemonic()) {
            closeDialog.run()
            CommitSessionCollector.getInstance(project).logCommitProblemViewed(problem, CommitProblemPlace.PUSH_DIALOG)
            problem.showDetails(project)
          }
        }
      }
      return panel
    }

    return null
  }
}

internal class PostCommitInfo(
  commitInfo: StaticCommitInfo,
  staticChanges: List<Change>
) : CommitInfo {
  override val commitContext: CommitContext = commitInfo.commitContext
  override val isVcsCommit: Boolean = commitInfo.isVcsCommit
  override val executor: CommitExecutor? = commitInfo.executor
  override val commitActionText: String = commitInfo.commitActionText
  override val committedChanges: List<Change> = staticChanges
  override val affectedVcses: List<AbstractVcs> = commitInfo.affectedVcses
  override val commitMessage: String = commitInfo.commitMessage
}
