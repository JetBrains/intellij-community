// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.ui.components.DropDownLink
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import javax.swing.JButton

internal interface GHAccountsHost {
  fun addAccount(server: GithubServerPath, login: String, token: String)
  fun isAccountUnique(login: String, server: GithubServerPath): Boolean

  companion object {
    val KEY: DataKey<GHAccountsHost> = DataKey.create("GHAccountsHost")

    fun createAddAccountLink(): JButton =
      DropDownLink(message("link.add.account"), {
        val group = ActionManager.getInstance().getAction("Github.Accounts.AddAccount") as ActionGroup
        val dataContext = DataManager.getInstance().getDataContext(it)

        JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext, ActionSelectionAid.MNEMONICS, false)
      })
  }
}