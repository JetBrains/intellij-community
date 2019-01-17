// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.DialogManager
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.util.GithubUtil
import java.awt.Component

internal class InteractiveGithubHttpAuthDataProvider(private val project: Project,
                                                     private val potentialAccounts: Collection<GithubAccount>,
                                                     private val authenticationManager: GithubAuthenticationManager) : InteractiveGitHttpAuthDataProvider {

  @CalledInAwt
  override fun getAuthData(parentComponent: Component?): AuthData? {
    val dialog = GithubChooseAccountDialog(project,
                                           parentComponent,
                                           potentialAccounts,
                                           null,
                                           false,
                                           true,
                                           "Choose GitHub Account",
                                           "Log In")
    DialogManager.show(dialog)
    if (!dialog.isOK) return null
    val account = dialog.account
    val token = invokeAndWaitIfNeeded(parentComponent?.let(ModalityState::stateForComponent) ?: ModalityState.any()) {
      authenticationManager.getTokenForAccount(account) ?: authenticationManager.requestNewToken(account, project, parentComponent)
    } ?: return null
    if (dialog.setDefault) authenticationManager.setDefaultAccount(project, account)
    return AuthData(GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE, token)
  }
}