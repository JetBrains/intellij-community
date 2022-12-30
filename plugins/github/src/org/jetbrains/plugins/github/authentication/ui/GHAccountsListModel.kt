// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsListModel
import com.intellij.collaboration.auth.ui.MutableAccountsListModel
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

class GHAccountsListModel : MutableAccountsListModel<GithubAccount, String>(),
                            AccountsListModel.WithDefault<GithubAccount, String>,
                            GHAccountsHost {

  override var defaultAccount: GithubAccount? = null

  override fun addAccount(server: GithubServerPath, login: String, token: String) {
    val account = GHAccountManager.createAccount(login, server)
    add(account, token)
  }

  override fun updateAccount(account: GithubAccount, token: String) {
    update(account, token)
  }

  override fun isAccountUnique(login: String, server: GithubServerPath): Boolean =
    accountsListModel.items.none { it.name == login && it.server.equals(server, true) }
}