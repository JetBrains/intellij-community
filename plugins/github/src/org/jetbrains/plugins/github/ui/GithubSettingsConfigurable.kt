// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.accounts.GHAccountsListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GHAccountsPanel
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubSettings
import org.jetbrains.plugins.github.util.GithubUtil

internal class GithubSettingsConfigurable internal constructor(private val project: Project) : BoundConfigurable(GithubUtil.SERVICE_DISPLAY_NAME, "settings.github") {
  override fun createPanel(): DialogPanel {
    val defaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
    val accountManager = service<GHAccountManager>()
    val settings = GithubSettings.getInstance()
    return panel {
      row {
        val accountsPanel = GHAccountsPanel(project, GithubApiRequestExecutor.Factory.getInstance(),
                                            CachingGHUserAvatarLoader.getInstance()).apply {
          Disposer.register(disposable!!, this)
        }
        component(accountsPanel)
          .onIsModified { accountsPanel.isModified(accountManager.accounts, defaultAccountHolder.account) }
          .onReset {
            val accountsMap = accountManager.accounts.associateWith { accountManager.findCredentials(it) }
            accountsPanel.setAccounts(accountsMap, defaultAccountHolder.account)
            accountsPanel.clearNewTokens()
            accountsPanel.loadExistingAccountsDetails()
          }
          .onApply {
            val (accountsTokenMap, defaultAccount) = accountsPanel.getAccounts()
            accountManager.updateAccounts(accountsTokenMap)
            defaultAccountHolder.account = defaultAccount
            accountsPanel.clearNewTokens()
          }

        ApplicationManager.getApplication().messageBus.connect(disposable!!)
          .subscribe(GHAccountManager.TOPIC, object : GHAccountsListener {
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