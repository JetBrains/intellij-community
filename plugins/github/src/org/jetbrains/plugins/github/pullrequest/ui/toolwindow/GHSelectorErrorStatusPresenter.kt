// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.github.i18n.GithubBundle
import javax.swing.Action

class GHSelectorErrorStatusPresenter : ErrorStatusPresenter<RepositoryAndAccountSelectorViewModel.Error> {
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
    is RepositoryAndAccountSelectorViewModel.Error.MissingCredentials -> GithubBundle.message("account.token.missing")
  }

  override fun getErrorAction(error: RepositoryAndAccountSelectorViewModel.Error): Action? = null
}