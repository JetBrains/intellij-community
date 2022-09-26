// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import kotlinx.coroutines.plus
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsListModel
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsPanelActionsController
import org.jetbrains.plugins.gitlab.util.GitLabUtil

internal class GitLabSettingsConfigurable(private val project: Project)
  : BoundConfigurable(GitLabUtil.SERVICE_DISPLAY_NAME, "settings.gitlab") {
  override fun createPanel(): DialogPanel {
    val accountManager = service<GitLabAccountManager>()
    val defaultAccountHolder = project.service<GitLabProjectDefaultAccountHolder>()

    val scope = DisposingMainScope(disposable!!) + ModalityState.any().asContextElement()
    val accountsModel = GitLabAccountsListModel()
    val detailsProvider = GitLabAccountsDetailsProvider(scope) { account ->
      accountsModel.newCredentials.getOrElse(account) {
        accountManager.findCredentials(account)
      }?.let {
        service<GitLabApiManager>().getClient(it)
      }
    }
    val actionsController = GitLabAccountsPanelActionsController(project, accountsModel)
    val accountsPanelFactory = AccountsPanelFactory(scope, accountManager, defaultAccountHolder, accountsModel)

    return panel {
      row {
        accountsPanelFactory.accountsPanelCell(this, detailsProvider, actionsController)
          .horizontalAlign(HorizontalAlign.FILL)
          .verticalAlign(VerticalAlign.FILL)
      }.resizableRow()
    }
  }
}