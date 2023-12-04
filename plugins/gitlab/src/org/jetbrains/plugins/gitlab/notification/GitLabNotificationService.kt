// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.notification

import com.intellij.collaboration.ui.notification.CollaborationToolsNotifier
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.action.GitLabMergeRequestOpenCreateTabNotificationAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

@Service(Service.Level.PROJECT)
internal class GitLabNotificationService(private val project: Project, parentCs: CoroutineScope) {
  private val serviceScope = parentCs.childScope(CoroutineName("GitLab notification service scope"))

  fun showReviewCreationNotification(targetBranch: String) {
    serviceScope.launch {
      val toolWindowVm = project.serviceAsync<GitLabToolWindowViewModel>()
      val projectVm = toolWindowVm.projectVm.filterNotNull().first()

      val defaultBranch = projectVm.defaultBranch.await()
      if (targetBranch.endsWith(defaultBranch)) return@launch

      val mergeRequest = projectVm.mergeRequestOnCurrentBranch.first()
      if (mergeRequest == null) {
        notifyReviewCreation(project)
      }
    }
  }

  private fun notifyReviewCreation(project: Project) {
    CollaborationToolsNotifier.getInstance(project).notifyBalloon(
      GitLabNotificationIdsHolder.MERGE_REQUEST_CREATE,
      GitLabBundle.message("merge.request.create.notification.title"),
      GitLabBundle.message("merge.request.create.notification.action.message"),
      GitLabMergeRequestOpenCreateTabNotificationAction()
    )
  }
}