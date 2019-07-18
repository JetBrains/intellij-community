// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.dvcs.DvcsUtil.getShortNames
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.UpdateSession
import com.intellij.util.containers.MultiMap
import git4idea.repo.GitRepository

/**
 * Ranges are null if update didn't start yet, in which case there are no new commits to display,
 * and the error notification is shown from the GitUpdateProcess itself.
 */
class GitUpdateSession(private val project: Project,
                       private val ranges: Map<GitRepository, HashRange>?,
                       private val result: Boolean,
                       private val skippedRoots: Map<GitRepository, String>) : UpdateSession {

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
      val repo = skippedRoots.keys.first()
      return "${getShortRepositoryName(repo)} was skipped (${skippedRoots[repo]})"
    }

    val prefix = "Skipped ${skippedRoots.size} repositories: <br/>"
    val grouped = groupByReasons(skippedRoots)
    if (grouped.keySet().size == 1) {
      val reason = grouped.keySet().first()
      return prefix + getShortNames(grouped.get(reason)) + " (" + reason + ")"
    }

    return prefix + grouped.keySet().joinToString("<br/>") { reason -> getShortNames(grouped.get(reason)) + " (" + reason + ")" }
  }

  private fun groupByReasons(skippedRoots: Map<GitRepository, String>): MultiMap<String, GitRepository> {
    val result = MultiMap.create<String, GitRepository>()
    skippedRoots.forEach { (file, s) -> result.putValue(s, file) }
    return result
  }

  override fun showNotification() {
    if (ranges != null) {
      GitUpdateInfoAsLog(project, ranges) { updatedFilesNumber, updatedCommitsNumber, filteredCommitsNumber, viewCommits ->
        val notification = prepareNotification(updatedFilesNumber, updatedCommitsNumber, filteredCommitsNumber)
        notification.addAction(NotificationAction.createSimple("View Commits", viewCommits))
        notification
      }.buildAndShowNotification()
    }
  }

  private fun prepareNotification(updatedFilesNumber: Int, updatedCommitsNumber: Int, filteredCommitsNumber: Int?): Notification {
    val title: String
    var content: String?
    val type: NotificationType
    val mainMessage = getTitleForUpdateNotification(updatedFilesNumber, updatedCommitsNumber)
    if (isCanceled) {
      title = "Project Partially Updated"
      content = mainMessage
      type = NotificationType.WARNING
    }
    else {
      title = mainMessage
      content = getBodyForUpdateNotification(updatedFilesNumber, updatedCommitsNumber, filteredCommitsNumber)
      type = NotificationType.INFORMATION
    }

    val additionalContent = getAdditionalNotificationContent()
    if (additionalContent != null) {
      if (content.isNotEmpty()) {
        content += "<br/>"
      }
      content += additionalContent
    }

    return VcsNotifier.STANDARD_NOTIFICATION.createNotification(title, content, type, null)
  }
}

fun getTitleForUpdateNotification(updatedFilesNumber: Int, updatedCommitsNumber: Int): String {
  val files = pluralize("file", updatedFilesNumber)
  val commits = pluralize("commit", updatedCommitsNumber)
  return "$updatedFilesNumber $files updated in $updatedCommitsNumber $commits"
}

fun getBodyForUpdateNotification(updatedFilesNumber: Int, updatedCommitsNumber: Int, filteredCommitsNumber: Int?): String {
  return when (filteredCommitsNumber) {
    null -> ""
    0 -> "No commits matching filters"
    else -> "$filteredCommitsNumber ${pluralize("commit", filteredCommitsNumber)} matching filters"
  }
}
