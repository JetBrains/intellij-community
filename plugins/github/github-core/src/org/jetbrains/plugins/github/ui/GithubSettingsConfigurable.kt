// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForMemoryOnlyPasswordSafeAndGet
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsProvider
import org.jetbrains.plugins.github.authentication.ui.GHAccountsListModel
import org.jetbrains.plugins.github.authentication.ui.GHAccountsPanelActionsController
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.GHPluginProjectScopeProvider
import org.jetbrains.plugins.github.util.GithubSettings
import org.jetbrains.plugins.github.util.GithubUtil

internal class GithubSettingsConfigurable internal constructor(
  private val project: Project
) : BoundConfigurable(GithubUtil.SERVICE_DISPLAY_NAME, "settings.github") {
  override fun createPanel(): DialogPanel {
    val scopeProvider = project.service<GHPluginProjectScopeProvider>()
    val defaultAccountHolder = project.service<GithubProjectDefaultAccountHolder>()
    val accountManager = service<GHAccountManager>()
    val ghSettings = GithubSettings.getInstance()

    val scope = scopeProvider.createDisposedScope(javaClass.name, disposable!!,
                                                  Dispatchers.EDT + ModalityState.any().asContextElement())
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
      row {
        checkBox(message("settings.automatically.mark.as.viewed"))
          .bindSelected(ghSettings::isAutomaticallyMarkAsViewed, ghSettings::setAutomaticallyMarkAsViewed)
      }
      row {
        checkBox(message("settings.enable.pr.seen.markers"))
          .bindSelected(ghSettings::isSeenMarkersEnabled, ghSettings::setIsSeenMarkersEnabled)
      }
      row(message("settings.timeout")) {
        intTextField(range = 0..60)
          .columns(2)
          .bindIntText({ ghSettings.connectionTimeout / 1000 }, { ghSettings.connectionTimeout = it * 1000 })
          .gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        label(message("settings.timeout.seconds"))
          .gap(RightGap.COLUMNS)

        addWarningForMemoryOnlyPasswordSafeAndGet(
          scope,
          service<GHAccountManager>().canPersistCredentials,
          ::panel
        ).align(AlignX.RIGHT)
      }
    }
  }
}
