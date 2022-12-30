// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.ui.CollectionComboBoxModel
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

internal class GHAccountsComboBoxModel(accounts: Set<GithubAccount>, selection: GithubAccount?) :
  CollectionComboBoxModel<GithubAccount>(accounts.toMutableList(), selection),
  GHAccountsHost {

  override fun addAccount(server: GithubServerPath, login: String, token: String) {
    val account = GithubAuthenticationManager.getInstance().registerAccount(login, server, token)

    add(account)
    selectedItem = account
  }

  override fun updateAccount(account: GithubAccount, token: String) {
    GithubAuthenticationManager.getInstance().updateAccountToken(account, token)
  }

  override fun isAccountUnique(login: String, server: GithubServerPath): Boolean =
    GithubAuthenticationManager.getInstance().isAccountUnique(login, server)
}