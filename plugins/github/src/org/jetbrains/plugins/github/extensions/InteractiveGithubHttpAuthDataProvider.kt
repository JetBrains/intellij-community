// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.DialogManager
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.JComponent

internal class InteractiveGithubHttpAuthDataProvider(private val project: Project,
                                                     private val potentialAccounts: Collection<GithubAccount>,
                                                     private val authenticationManager: GithubAuthenticationManager) : InteractiveGitHttpAuthDataProvider {

  @CalledInAwt
  override fun getAuthData(parentComponent: JComponent?): AuthData? {
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
    if (dialog.setDefault) authenticationManager.setDefaultAccount(project, account)
    return AuthData(GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE, authenticationManager.getTokenForAccount(account))
  }
}