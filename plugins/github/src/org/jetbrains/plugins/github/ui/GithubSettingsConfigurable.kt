// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.credentialStore.PasswordSafeSettings
import com.intellij.credentialStore.ProviderType
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.plus
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsProvider
import org.jetbrains.plugins.github.authentication.ui.GHAccountsListModel
import org.jetbrains.plugins.github.authentication.ui.GHAccountsPanelActionsController
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.GithubSettings
import org.jetbrains.plugins.github.util.GithubUtil

internal class GithubSettingsConfigurable internal constructor(private val project: Project)
  : BoundConfigurable(GithubUtil.SERVICE_DISPLAY_NAME, "settings.github") {
  override fun createPanel(): DialogPanel {
    val passwordSafeSettings = service<PasswordSafeSettings>()

    val defaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
    val accountManager = service<GHAccountManager>()
    val ghSettings = GithubSettings.getInstance()

    val scope = DisposingMainScope(disposable!!) + ModalityState.any().asContextElement()
    val accountsModel = GHAccountsListModel()
    val detailsProvider = GHAccountsDetailsProvider(scope, accountManager, accountsModel)

    val panelFactory = AccountsPanelFactory(scope, accountManager, defaultAccountHolder, accountsModel)
    val actionsController = GHAccountsPanelActionsController(project, accountsModel)

    return panel {
      row {
        panelFactory.accountsPanelCell(this, detailsProvider, actionsController)
          .align(Align.FILL)
      }.resizableRow()

      row {
        checkBox(message("settings.clone.ssh"))
          .bindSelected(ghSettings::isCloneGitUsingSsh, ghSettings::setCloneGitUsingSsh)
      }
      row(message("settings.timeout")) {
        intTextField(range = 0..60)
          .columns(2)
          .bindIntText({ ghSettings.connectionTimeout / 1000 }, { ghSettings.connectionTimeout = it * 1000 })
          .gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        label(message("settings.timeout.seconds"))
          .gap(RightGap.COLUMNS)

        panel {
          row {
            label(message("accounts.error.password.not.saved")).applyToComponent {
              foreground = NamedColorUtil.getErrorForeground()
            }
            link(message("accounts.error.password.not.saved.link")) {
              val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(it.source as ActionLink))
              settings?.select(settings.find("application.passwordSafe"))
            }
          }
        }
          .align(AlignX.RIGHT)
          .visible(passwordSafeSettings.state.provider == ProviderType.MEMORY_ONLY)
      }
    }
  }
}
