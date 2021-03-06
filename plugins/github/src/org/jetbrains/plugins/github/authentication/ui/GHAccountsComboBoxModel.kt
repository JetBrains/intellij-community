// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GHAccountsHost.Companion.createAddAccountLink
import org.jetbrains.plugins.github.i18n.GithubBundle.message

internal class GHAccountsComboBoxModel(accounts: Set<GithubAccount>, selection: GithubAccount?) :
  CollectionComboBoxModel<GithubAccount>(accounts.toMutableList(), selection),
  GHAccountsHost {

  override fun addAccount(server: GithubServerPath, login: String, token: String) {
    val account = GithubAuthenticationManager.getInstance().registerAccount(login, server, token)

    add(account)
    selectedItem = account
  }

  override fun isAccountUnique(login: String, server: GithubServerPath): Boolean =
    GithubAuthenticationManager.getInstance().isAccountUnique(login, server)

  companion object {
    fun Row.accountSelector(model: CollectionComboBoxModel<GithubAccount>, onChange: (() -> Unit)? = null) =
      cell {
        comboBox(model, { model.selected }, { })
          .constraints(pushX, growX)
          .withValidationOnApply { if (model.selected == null) error(message("dialog.message.account.cannot.be.empty")) else null }
          .applyToComponent {
            if (onChange != null) addActionListener { onChange() }
          }

        if (model.size == 0) {
          createAddAccountLink()().withLargeLeftGap()
        }
      }
  }
}