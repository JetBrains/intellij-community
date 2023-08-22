// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.git.http

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.http.HostedGitAuthenticationFailureManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager

@Service(Service.Level.PROJECT)
class GitLabGitAuthenticationFailureManager(project: Project)
  : HostedGitAuthenticationFailureManager<GitLabAccount>(accountManager = { service<GitLabAccountManager>() }) {
  override fun dispose() = Unit
}