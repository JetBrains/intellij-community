// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.notification

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.push.GitPushListener
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository

internal class GitLabPushNotificationListener(private val project: Project) : GitPushListener {
  override fun onCompleted(repository: GitRepository, pushResult: GitPushRepoResult) {
    when (pushResult.type) {
      GitPushRepoResult.Type.SUCCESS,
      GitPushRepoResult.Type.NEW_BRANCH,
      GitPushRepoResult.Type.FORCED -> {
        project.service<GitLabNotificationService>().showReviewCreationNotification(pushResult.targetBranch)
      }
      else -> {}
    }
  }
}