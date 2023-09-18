// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.list

import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import javax.swing.Action

class GHPRErrorStatusPresenter(
  project: Project,
  account: GithubAccount,
  resetRunnable: () -> Unit
) : ErrorStatusPresenter<Throwable> {
  private val errorHandler = GHApiLoadingErrorHandler(project, account, resetRunnable)

  override fun getErrorTitle(error: Throwable): @Nls String = GithubBundle.message("pull.request.list.cannot.load")

  override fun getErrorDescription(error: Throwable): @Nls String {
    return if (error is GithubAuthenticationException) GithubBundle.message("pull.request.list.error.authorization")
    else GHHtmlErrorPanel.getLoadingErrorText(error)
  }

  override fun getErrorAction(error: Throwable): Action = errorHandler.getActionForError(error)
}