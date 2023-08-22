// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import kotlinx.coroutines.plus
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsProvider
import org.jetbrains.plugins.github.authentication.ui.GHAccountsListModel
import org.jetbrains.plugins.github.authentication.ui.GHAccountsPanelActionsController
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubSettings
import org.jetbrains.plugins.github.util.GithubUtil

internal class GithubSettingsConfigurable internal constructor(private val project: Project)
  : BoundConfigurable(GithubUtil.SERVICE_DISPLAY_NAME, "settings.github") {
  override fun createPanel(): DialogPanel {
    val defaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
    val accountManager = service<GHAccountManager>()
    val settings = GithubSettings.getInstance()

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
        checkBox(GithubBundle.message("settings.clone.ssh"))
          .bindSelected(settings::isCloneGitUsingSsh, settings::setCloneGitUsingSsh)
      }
      row(GithubBundle.message("settings.timeout")) {
        intTextField(range = 0..60)
          .columns(2)
          .bindIntText({ settings.connectionTimeout / 1000 }, { settings.connectionTimeout = it * 1000 })
          .gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        label(GithubBundle.message("settings.timeout.seconds"))
      }
    }
  }
}
