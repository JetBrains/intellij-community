// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsLoader
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsListModel
import org.jetbrains.plugins.gitlab.util.GitLabUtil

internal class GitLabSettingsConfigurable(private val project: Project)
  : BoundConfigurable(GitLabUtil.SERVICE_DISPLAY_NAME, "settings.gitlab") {
  override fun createPanel(): DialogPanel {
    val accountManager = service<GitLabAccountManager>()
    val defaultAccountHolder = project.service<GitLabProjectDefaultAccountHolder>()

    val accountsModel = GitLabAccountsListModel(project)
    val scope = CoroutineScope(SupervisorJob()).also { Disposer.register(disposable!!) { it.cancel() } }
    val detailsLoader = GitLabAccountsDetailsLoader(scope, accountManager, accountsModel)
    val accountsPanelFactory = AccountsPanelFactory(accountManager, defaultAccountHolder, accountsModel, detailsLoader, disposable!!)

    return panel {
      row {
        accountsPanelFactory.accountsPanelCell(this, true)
          .horizontalAlign(HorizontalAlign.FILL)
          .verticalAlign(VerticalAlign.FILL)
      }.resizableRow()
    }
  }
}