// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.selector

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.github.authentication.AuthorizationType
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

class GHSelectorErrorStatusPresenter(
  private val project: Project,
  private val loginSource: GHLoginSource,
  private val resetAction: () -> Unit = {}
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
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> ExceptionUtil.getPresentableMessage(error.exception)
    is RepositoryAndAccountSelectorViewModel.Error.MissingCredentials -> CollaborationToolsBundle.message("account.token.missing")
  }

  override fun getErrorAction(error: RepositoryAndAccountSelectorViewModel.Error): Action? = when (error) {
    is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> LogInAgain(project, error.account as GithubAccount, loginSource, resetAction)
    else -> null
  }

  private class LogInAgain(
    private val project: Project,
    private val account: GithubAccount,
    private val loginSource: GHLoginSource,
    private val resetAction: () -> Unit
  ) : AbstractAction(CollaborationToolsBundle.message("login.again.action.text")) {
    override fun actionPerformed(event: ActionEvent) {
      val parentComponent = event.source as? JComponent ?: return
      val authData = GHAccountsUtil.requestReLogin(account, project, parentComponent, authType = AuthorizationType.UNDEFINED, loginSource = loginSource)
      if (authData != null) {
        resetAction()
      }
    }
  }
}