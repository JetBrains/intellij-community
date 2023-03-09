// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModel
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.exception.GitLabHttpStatusErrorAction
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.Action

internal class GitLabSelectorErrorStatusPresenter(
  private val project: Project,
  private val parentScope: CoroutineScope,
  private val accountManager: GitLabAccountManager,
  private val resetAction: () -> Unit
) : ErrorStatusPresenter<RepositoryAndAccountSelectorViewModel.Error> {
  override fun getErrorTitle(error: RepositoryAndAccountSelectorViewModel.Error): String = when (error) {
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> CollaborationToolsBundle.message(
      "review.list.connection.failed.repository.account",
      error.repo.repository,
      error.account
    )
    is RepositoryAndAccountSelectorViewModel.Error.MissingCredentials -> CollaborationToolsBundle.message("review.list.connection.failed")
  }

  override fun getErrorDescription(error: RepositoryAndAccountSelectorViewModel.Error): String = when (error) {
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> ExceptionUtil.getPresentableMessage(error.exception)
    is RepositoryAndAccountSelectorViewModel.Error.MissingCredentials -> GitLabBundle.message("account.token.missing")
  }

  override fun getErrorAction(error: RepositoryAndAccountSelectorViewModel.Error): Action? = when (error) {
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> GitLabHttpStatusErrorAction.LogInAgain(
      project, parentScope,
      account = error.account as GitLabAccount,
      accountManager = accountManager,
      resetAction
    )
    else -> null
  }
}