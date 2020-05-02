// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

class GHLoadingErrorHandlerImpl(private val project: Project,
                                private val account: GithubAccount,
                                private val resetRunnable: () -> Unit)
  : GHLoadingErrorHandler {

  override fun getActionForError(error: Throwable): Action? {
    if (error is GithubAuthenticationException) {
      return ReLoginAction()
    }
    else {
      return RetryAction()
    }
  }

  private inner class ReLoginAction : AbstractAction(GithubBundle.message("accounts.relogin")) {
    override fun actionPerformed(e: ActionEvent?) {
      if (GithubAuthenticationManager.getInstance().requestReLogin(account, project))
        resetRunnable()
    }
  }

  private inner class RetryAction : AbstractAction(GithubBundle.message("retry.action")) {
    override fun actionPerformed(e: ActionEvent?) {
      resetRunnable()
    }
  }
}