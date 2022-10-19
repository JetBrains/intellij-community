// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotificationIdsHolder
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.checkin.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*

@Service(Service.Level.PROJECT)
internal class PostCommitChecksHandler(val project: Project) {
  companion object {
    private val LOG = logger<PostCommitChecksHandler>()

    fun getInstance(project: Project): PostCommitChecksHandler = project.service()
  }

  fun canHandle(commitInfo: CommitInfo): Boolean {
    return commitInfo.affectedVcses.isNotEmpty() &&
           commitInfo.affectedVcses.all { vcs -> vcs.checkinEnvironment?.postCommitChangeConverter != null }
  }

  @RequiresEdt
  fun startPostCommitChecksTask(commitInfo: StaticCommitInfo, commitChecks: List<CommitCheck>) {
    val scope = CoroutineScope(CoroutineName("post commit checks") + Dispatchers.IO)
    scope.launch {
      withBackgroundProgressIndicator(project, VcsBundle.message("post.commit.checks.progress.text")) {
        val postCommitInfo = createPostCommitInfo(commitInfo)
        withContext(Dispatchers.EDT) {
          val problems = runCommitChecks(commitChecks, postCommitInfo)
          if (problems.isEmpty()) return@withContext

          reportPostCommitChecksFailure(problems)
        }
      }
    }
  }

  private suspend fun runCommitChecks(commitChecks: List<CommitCheck>,
                                      postCommitInfo: StaticCommitInfo): List<CommitProblem> {
    val problems = mutableListOf<CommitProblem>()

    if (DumbService.isDumb(project)) {
      if (commitChecks.any { !DumbService.isDumbAware(it) }) {
        problems += TextCommitProblem(VcsBundle.message("before.checkin.post.commit.error.dumb.mode"))
      }
    }

    for (commitCheck in commitChecks) {
      problems += AbstractCommitWorkflow.runCommitCheck(project, commitCheck, postCommitInfo) ?: continue
    }
    return problems
  }

  private fun createPostCommitInfo(commitInfo: StaticCommitInfo): StaticCommitInfo {
    val changeConverters = commitInfo.affectedVcses.mapNotNull { vcs -> vcs.checkinEnvironment?.postCommitChangeConverter }
    if (changeConverters.isEmpty()) LOG.error("Post-commit change converters not found for ${commitInfo.affectedVcses}")

    val commitContext = commitInfo.commitContext
    commitContext.isPostCommitCheck = true

    val staticChanges = mutableListOf<Change>()
    try {
      for (changeConverter in changeConverters) {
        staticChanges += changeConverter.collectChangesAfterCommit(commitContext)
      }
      if (staticChanges.isEmpty()) {
        LOG.warn("Post-commit converters returned empty list of changes")
        staticChanges += commitInfo.committedChanges
      }
    }
    catch (e: VcsException) {
      LOG.warn(e)
      staticChanges.clear()
      staticChanges += commitInfo.committedChanges
    }

    return StaticCommitInfo(commitContext, commitInfo.executor, commitInfo.commitActionText,
                            staticChanges, commitInfo.affectedVcses, commitInfo.commitMessage)
  }

  private fun reportPostCommitChecksFailure(problems: List<CommitProblem>) {
    val notification = VcsNotifier.IMPORTANT_ERROR_NOTIFICATION
      .createNotification(VcsBundle.message("post.commit.checks.failed.notification.title"),
                          problems.joinToString("<br>") { it.text },
                          NotificationType.ERROR)
      .setDisplayId(VcsNotificationIdsHolder.POST_COMMIT_CHECKS_FAILED)

    for (problem in problems.filterIsInstance<CommitProblemWithDetails>()) {
      notification.addAction(NotificationAction.createSimple(problem.showDetailsAction) {
        problem.showDetails(project)
      })
    }

    notification.notify(project)
  }
}