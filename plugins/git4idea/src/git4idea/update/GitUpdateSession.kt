// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.dvcs.DvcsUtil.getShortNames
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.UpdateSession
import com.intellij.util.containers.MultiMap
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

/**
 * Ranges are null if update didn't start yet, in which case there are no new commits to display,
 * and the error notification is shown from the GitUpdateProcess itself.
 */
class GitUpdateSession(
  private val project: Project,
  private val notificationData: GitUpdateInfoAsLog.NotificationData?,
  private val result: Boolean,
  private val skippedRoots: Map<GitRepository, String>,
) : UpdateSession {

  override fun getExceptions(): List<VcsException> {
    return emptyList()
  }

  override fun onRefreshFilesCompleted() {}

  override fun isCanceled(): Boolean {
    return !result
  }

  override fun getAdditionalNotificationContent(): String? {
    if (skippedRoots.isEmpty()) return null

    if (skippedRoots.size == 1) {
      val (repo, reason) = skippedRoots.entries.first()
      return GitBundle.message("git.update.repo.was.skipped", HtmlChunk.text(getShortRepositoryName(repo)), HtmlChunk.text(reason))
    }

    val prefix = HtmlChunk.text(GitBundle.message("git.update.skipped.repositories", skippedRoots.size)) // NON-NLS
    val grouped = groupByReasons(skippedRoots)
    if (grouped.entrySet().size == 1) {
      val (reason, repos) = grouped.entrySet().first()
      return HtmlBuilder().append(prefix).br()
        .append(mentionSkippedRoots(repos, reason))
        .toString()
    }

    return HtmlBuilder().append(prefix).br()
      .appendWithSeparators(HtmlChunk.br(), grouped.entrySet().map { (reason, repos) -> mentionSkippedRoots(repos, reason) })
      .toString()
  }

  private fun mentionSkippedRoots(repos: Collection<GitRepository>, reason: @Nls String): HtmlChunk {
    return HtmlChunk.text(getShortNames(repos) + " (" + reason + ")")
  }

  private fun groupByReasons(skippedRoots: Map<GitRepository, String>): MultiMap<String, GitRepository> {
    val result = MultiMap.create<String, GitRepository>()
    skippedRoots.forEach { (file, s) -> result.putValue(s, file) }
    return result
  }

  override fun showNotification() {
    if (notificationData != null) {
      val notification = prepareNotification(notificationData.updatedFilesCount,
                                             notificationData.receivedCommitsCount,
                                             notificationData.filteredCommitsCount)

      notification.addAction(NotificationAction.createSimple(Supplier { GitBundle.message("action.NotificationAction.GitUpdateSession.text.view.commits") },
                                                             notificationData.viewCommitAction))

      GitPostUpdateHandler.getActions(project, notificationData.ranges).forEach { notification.addAction(it) }

      VcsNotifier.getInstance(project).notify(notification)
    }
  }

  private fun prepareNotification(
    updatedFilesNumber: Int,
    updatedCommitsNumber: Int,
    filteredCommitsNumber: Int?,
  ): Notification {
    val title: String
    var content: String?
    val type: NotificationType
    val displayId: String
    val mainMessage = getTitleForUpdateNotification(updatedFilesNumber, updatedCommitsNumber)
    if (isCanceled) {
      title = GitBundle.message("git.update.project.partially.updated.title")
      content = mainMessage
      type = NotificationType.WARNING
      displayId = GitNotificationIdsHolder.PROJECT_PARTIALLY_UPDATED
    }
    else {
      title = mainMessage
      content = getBodyForUpdateNotification(filteredCommitsNumber)
      type = NotificationType.INFORMATION
      displayId = GitNotificationIdsHolder.PROJECT_UPDATED
    }

    val additionalContent = additionalNotificationContent
    if (additionalContent != null) {
      if (content.isNotEmpty()) {
        content += "<br/>" // NON-NLS
      }
      content += additionalContent
    }

    return VcsNotifier.standardNotification().createNotification(title, content, type).also { it.setDisplayId(displayId) }
  }
}

@NlsContexts.NotificationTitle
fun getTitleForUpdateNotification(updatedFilesNumber: Int, updatedCommitsNumber: Int): String =
  GitBundle.message("git.update.files.updated.in.commits", updatedFilesNumber, updatedCommitsNumber)

@NlsContexts.NotificationContent
fun getBodyForUpdateNotification(filteredCommitsNumber: Int?): String {
  return when (filteredCommitsNumber) {
    null -> ""
    0 -> GitBundle.message("git.update.no.commits.matching.filters")
    else -> GitBundle.message("git.update.commits.matching.filters", filteredCommitsNumber)
  }
}
