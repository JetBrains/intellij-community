// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.collaboration.auth.AccountsListener
import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.collaboration.auth.ui.SimpleAccountsListCellRenderer
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.ide.DataManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsProvider
import org.jetbrains.plugins.github.authentication.ui.GHAccountsHost
import org.jetbrains.plugins.github.authentication.ui.GHAccountsListModel
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubSettings
import org.jetbrains.plugins.github.util.GithubUtil

internal class GithubSettingsConfigurable internal constructor(private val project: Project)
  : BoundConfigurable(GithubUtil.SERVICE_DISPLAY_NAME, "settings.github") {
  override fun createPanel(): DialogPanel {
    val defaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
    val accountManager = service<GHAccountManager>()
    val settings = GithubSettings.getInstance()

    val indicatorsProvider = ProgressIndicatorsProvider().also {
      Disposer.register(disposable!!, it)
    }
    val accountsModel = GHAccountsListModel(project)
    val detailsProvider = GHAccountsDetailsProvider(indicatorsProvider, accountManager, accountsModel)
    accountsModel.addTokenChangeListener(detailsProvider::reset)
    detailsProvider.loadingStateModel.addInvokeListener {
      accountsModel.busyStateModel.value = it
    }

    val accountsPanel = AccountsPanelFactory.create(accountsModel) {
      SimpleAccountsListCellRenderer(accountsModel, detailsProvider)
    }.also {
      DataManager.registerDataProvider(it) { key ->
        if (GHAccountsHost.KEY.`is`(key)) accountsModel
        else null
      }
    }

    return panel {
      row {
        accountsPanel(grow, push)
          .onIsModified {
            accountsModel.newCredentials.isNotEmpty()
            || accountsModel.accounts != accountManager.accounts
            || accountsModel.defaultAccount != defaultAccountHolder.account
          }
          .onReset {
            accountsModel.accounts = accountManager.accounts
            accountsModel.defaultAccount = defaultAccountHolder.account
            accountsModel.clearNewCredentials()
            detailsProvider.resetAll()
          }
          .onApply {
            val newTokensMap = mutableMapOf<GithubAccount, String?>()
            newTokensMap.putAll(accountsModel.newCredentials)
            for (account in accountsModel.accounts) {
              newTokensMap.putIfAbsent(account, null)
            }
            val defaultAccount = accountsModel.defaultAccount
            accountManager.updateAccounts(newTokensMap)
            defaultAccountHolder.account = defaultAccount
            accountsModel.clearNewCredentials()
          }

        accountManager.addListener(disposable!!, object : AccountsListener<GithubAccount> {
          override fun onAccountCredentialsChanged(account: GithubAccount) {
            if (!isModified) reset()
          }
        })
      }
      row {
        checkBox(GithubBundle.message("settings.clone.ssh"), settings::isCloneGitUsingSsh, settings::setCloneGitUsingSsh)
      }
      row {
        cell {
          label(GithubBundle.message("settings.timeout"))
          intTextField({ settings.connectionTimeout / 1000 }, { settings.connectionTimeout = it * 1000 }, columns = 2, range = 0..60)
          @Suppress("DialogTitleCapitalization")
          label(GithubBundle.message("settings.timeout.seconds"))
        }
      }
    }
  }
}