// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.impl.VcsProjectLog
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

internal abstract class IssueIDPrePushHandler : AbstractIntelliJProjectPrePushHandler() {
  protected open val acceptableProjects = listOf(
    "KTIJ", "KTNB", "KT", "IDEA", "IJPL"
  )
  protected abstract val commitMessageRegex: Regex
  protected open val ignorePattern: Regex = Regex("(?!.*)")

  protected open val paths: List<String> = listOf()
  protected open val pathsToIgnore: List<String> = listOf("/test/", "/testData/")
  protected open val validateCommitsOnlyFromCurrentUser: Boolean = false

  final override fun validate(project: Project, info: PushInfo, indicator: ProgressIndicator): PushInfoValidationResult {
    val commitsToWarnAbout = info.commits.filterNot { isCommitValid(project, it) }
    if (commitsToWarnAbout.isEmpty()) {
      return PushInfoValidationResult.VALID
    }

    val skip = handleCommitsValidationFailure(project, info, commitsToWarnAbout, indicator.modalityState)
    if (skip) return PushInfoValidationResult.SKIP

    return PushInfoValidationResult.INVALID
  }

  private fun isCommitValid(project: Project, commit: VcsFullCommitDetails): Boolean {
    if (validateCommitsOnlyFromCurrentUser) {
      val currentUsers = VcsProjectLog.getInstance(project).dataManager?.userNameResolver?.resolveCurrentUser(commit.root)
      val committer = commit.committer
      // allow commits from other people:
      if (currentUsers != null && committer.email !in currentUsers.map { it.email }) {
        return true
      }
    }
    val commitPaths = commit.changes.asSequence().flatMap { change ->
      sequenceOf(change.beforeRevision?.path, change.afterRevision?.path).filterNotNull()
    }
    if (!containSources(commitPaths)) {
      return true
    }

    return isCommitMessageCorrect(commit.fullMessage)
  }

  @VisibleForTesting
  fun containSources(sourcePaths: Sequence<Path>): Boolean = sourcePaths.anyIn(paths, pathsToIgnore)

  /**
   * Notify the user about commits that don't pass validation.
   * Commits in [commitsToWarnAbout] are ordered from oldest to newest.
   *
   * @return `true` if user decided to skip validation, `false` otherwise.
   */
  @RequiresBackgroundThread
  protected open fun handleCommitsValidationFailure(
    project: Project,
    info: PushInfo,
    commitsToWarnAbout: List<VcsFullCommitDetails>,
    modalityState: ModalityState,
  ): Boolean {
    val lastAcceptableProjectIndex = acceptableProjects.lastIndex
    val acceptableProjectIssueLinks = acceptableProjects.mapIndexed { index, projectId ->
      val link = getProjectIssueLink(projectId)
      val suffix = when(index) {
        lastAcceptableProjectIndex -> ""
        lastAcceptableProjectIndex - 1 -> " or "
        else -> ", "
      }
      link + suffix
    }.joinToString("")

    val commitsInfo = commitsToWarnAbout.toHtml()

    val commitAsIs = invokeAndWait(modalityState) {
      @Suppress("DialogTitleCapitalization")
      MessageDialogBuilder.yesNo(
        DevKitGitBundle.message("push.commit.message.lacks.issue.reference.title"),
        DevKitGitBundle.message(
          "push.commit.message.lacks.issue.reference.body",
          acceptableProjectIssueLinks,
          commitsInfo
        )
      )
        .yesText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project = null)
    }

    return commitAsIs
  }

  fun isCommitMessageCorrect(message: String): Boolean {
    if (message == "Rename .java to .kt") {
      return true
    }
    return message.matches(commitMessageRegex) || message.matches(ignorePattern)
  }

  protected fun buildRegexFromAcceptableProjects(): Regex {
    @Suppress("RegExpUnnecessaryNonCapturingGroup")
    return Regex(
      ".*(?:${acceptableProjects.joinToString("|")})-\\d+.*",
      RegexOption.DOT_MATCHES_ALL /* line breaks matter */
    )
  }

  private fun getProjectIssueLink(projectId: String) =
    "<a href=\"https://youtrack.jetbrains.com/newIssue?project=$projectId\">$projectId</a>"
}