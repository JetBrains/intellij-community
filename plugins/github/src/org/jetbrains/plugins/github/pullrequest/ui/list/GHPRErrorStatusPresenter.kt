// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.list

import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel

fun GHPRErrorStatusPresenter(
  project: Project,
  account: GithubAccount,
  resetRunnable: () -> Unit
): ErrorStatusPresenter.Text<Throwable> {
  val errorHandler = GHApiLoadingErrorHandler(project, account, resetRunnable)
  return ErrorStatusPresenter.simple(
    GithubBundle.message("pull.request.list.cannot.load"),
    descriptionProvider = { error ->
      if (error is GithubAuthenticationException) GithubBundle.message("pull.request.list.error.authorization")
      else GHHtmlErrorPanel.getLoadingErrorText(error)
    },
    actionProvider = errorHandler::getActionForError
  )
}
