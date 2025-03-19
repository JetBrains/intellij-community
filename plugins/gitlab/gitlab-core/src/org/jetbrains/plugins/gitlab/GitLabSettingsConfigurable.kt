// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForMemoryOnlyPasswordSafeAndGet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.*
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsListModel
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsPanelActionsController
import org.jetbrains.plugins.gitlab.ui.util.GitLabPluginProjectScopeProvider
import org.jetbrains.plugins.gitlab.util.GitLabBundle.message
import org.jetbrains.plugins.gitlab.util.GitLabUtil

internal class GitLabSettingsConfigurable(private val project: Project)
  : BoundConfigurable(GitLabUtil.SERVICE_DISPLAY_NAME, "settings.gitlab") {
  override fun createPanel(): DialogPanel {
    val scopeProvider = project.service<GitLabPluginProjectScopeProvider>()
    val accountManager = service<GitLabAccountManager>()
    val defaultAccountHolder = project.service<GitLabProjectDefaultAccountHolder>()

    val scope = scopeProvider.createDisposedScope(javaClass.name, disposable!!,
                                                  Dispatchers.EDT + ModalityState.any().asContextElement())
    val accountsModel = GitLabAccountsListModel()
    val detailsProvider = GitLabAccountsDetailsProvider(scope, accountsModel) { account ->
      accountsModel.newCredentials.getOrElse(account) {
        accountManager.findCredentials(account)
      }?.let {
        service<GitLabApiManager>().getClient(account.server, it)
      }
    }
    val actionsController = GitLabAccountsPanelActionsController(project, accountsModel)
    val accountsPanelFactory = AccountsPanelFactory(scope, accountManager, defaultAccountHolder, accountsModel)

    val glSettings = GitLabSettings.getInstance()

    return panel {
      row {
        accountsPanelFactory.accountsPanelCell(this, detailsProvider, actionsController)
          .align(Align.FILL)
      }.resizableRow()

      row {
        checkBox(message("settings.automatically.mark.as.viewed"))
          .bindSelected({ glSettings.isAutomaticallyMarkAsViewed }, { glSettings.isAutomaticallyMarkAsViewed = it })
      }

      addWarningForMemoryOnlyPasswordSafeAndGet(
        scope,
        service<GitLabAccountManager>().canPersistCredentials,
        ::panel
      ).align(AlignX.RIGHT)
    }
  }
}

@Service(Service.Level.APP)
@State(name = "GitLabSettings", storages = [Storage("gitlab.xml")], category = SettingsCategory.TOOLS)
internal class GitLabSettings : SerializablePersistentStateComponent<GitLabSettings.State>(State()) {
  @Serializable
  data class State(
    val isAutomaticallyMarkAsViewed: Boolean = false
  )

  var isAutomaticallyMarkAsViewed: Boolean
    get() = state.isAutomaticallyMarkAsViewed
    set(value) {
      updateState { it.copy(isAutomaticallyMarkAsViewed = value) }
    }

  companion object {
    fun getInstance() = ApplicationManager.getApplication().service<GitLabSettings>()
  }
}
