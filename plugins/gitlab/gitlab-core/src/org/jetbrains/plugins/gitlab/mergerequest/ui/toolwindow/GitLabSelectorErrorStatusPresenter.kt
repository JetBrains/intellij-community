// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModel
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.exception.GitLabHttpStatusErrorAction
import javax.swing.Action

internal class GitLabSelectorErrorStatusPresenter(
  private val project: Project,
  private val parentScope: CoroutineScope,
  private val accountManager: GitLabAccountManager,
  private val loginSource: GitLabLoginSource,
  private val resetAction: () -> Unit
) : ErrorStatusPresenter.Text<RepositoryAndAccountSelectorViewModel.Error> {
  override fun getErrorTitle(error: RepositoryAndAccountSelectorViewModel.Error): String = when (error) {
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> CollaborationToolsBundle.message(
      "review.list.connection.failed.repository.account",
      error.repo.repository,
      error.account
    )
    is RepositoryAndAccountSelectorViewModel.Error.MissingCredentials -> CollaborationToolsBundle.message("review.list.connection.failed")
  }

  override fun getErrorDescription(error: RepositoryAndAccountSelectorViewModel.Error): String = when (error) {
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> when {
      isAuthorizationException(error.exception) -> CollaborationToolsBundle.message("account.token.invalid")
      else -> ExceptionUtil.getPresentableMessage(error.exception)
    }
    is RepositoryAndAccountSelectorViewModel.Error.MissingCredentials -> CollaborationToolsBundle.message("account.token.missing")
  }

  override fun getErrorAction(error: RepositoryAndAccountSelectorViewModel.Error): Action? = when (error) {
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> GitLabHttpStatusErrorAction.LogInAgain(
      project, parentScope,
      account = error.account as GitLabAccount,
      accountManager = accountManager,
      loginSource = loginSource,
      resetAction = resetAction
    )
    else -> null
  }

  companion object {
    fun isAuthorizationException(exception: Throwable): Boolean =
      exception is HttpStatusErrorException && exception.statusCode == 401
  }
}
