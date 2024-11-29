// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.exception.GitLabHttpStatusErrorAction
import javax.swing.Action

internal interface GitLabAccountViewModel {
  fun loginAction(): Action
}

internal class GitLabAccountViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val account: GitLabAccount,
  private val accountManager: GitLabAccountManager
) : GitLabAccountViewModel {
  private val cs: CoroutineScope = parentCs.childScope("GitLab Account VM")

  override fun loginAction(): Action {
    return GitLabHttpStatusErrorAction.LogInAgain(project, cs, account, accountManager)
  }
}