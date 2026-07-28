// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForEnabledCredentialHelper
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForMemoryOnlyPasswordSafeAndGet
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import git4idea.config.GitVcsApplicationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.authentication.GitLabOAuthSettings
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
  private lateinit var panel: DialogPanel

  override fun createPanel(): DialogPanel {
    val scopeProvider = project.service<GitLabPluginProjectScopeProvider>()
    val accountManager = service<GitLabAccountManager>()
    val defaultAccountHolder = project.service<GitLabProjectDefaultAccountHolder>()

    val scope = scopeProvider.createDisposedScope(javaClass.name, disposable!!,
                                                  Dispatchers.EDT + ModalityState.any().asContextElement())
    val accountsModel = GitLabAccountsListModel()
    val apiManager = service<GitLabApiManager>()
    val detailsProvider = GitLabAccountsDetailsProvider(scope, apiManager, accountManager, accountsModel)
    val actionsController = GitLabAccountsPanelActionsController(project, accountsModel)
    val accountsPanelFactory = AccountsPanelFactory(scope, accountManager, defaultAccountHolder, accountsModel)

    val glSettings = GitLabSettings.getInstance()
    val oauthSettings = GitLabOAuthSettings.getInstance(project)

    panel = panel {
      row {
        accountsPanelFactory.accountsPanelCell(this, detailsProvider, actionsController)
          .align(Align.FILL)
      }.resizableRow()

      row {
        checkBox(message("settings.automatically.mark.as.viewed"))
          .bindSelected({ glSettings.isAutomaticallyMarkAsViewed }, { glSettings.isAutomaticallyMarkAsViewed = it })
      }

      row {
        checkBox(message("settings.cloneUsingSsh"))
          .bindSelected({ glSettings.isCloneGitUsingSsh }, { glSettings.isCloneGitUsingSsh = it })
      }

      collapsibleGroup(message("settings.oauth.custom.server.oauth.label")) {
        row {
          textArea()
            .bindText(
              { oauthSettings.clientIdsText },
              { oauthSettings.clientIdsText = it }
            )
            .validationOnApply { validateOAuthConfig(it) }
            .contextHelp(message("settings.oauth.custom.server.oauth.example.label"))
            .align(AlignX.FILL)
            .resizableColumn()
        }.resizableRow()
      }
      addWarningForMemoryOnlyPasswordSafeAndGet(
        scope,
        service<GitLabAccountManager>().canPersistCredentials,
        ::panel
      ).align(AlignX.LEFT)

      addWarningForEnabledCredentialHelper(GitVcsApplicationSettings.getInstance().isUseCredentialHelper, ::panel)
        .align(AlignX.LEFT)
    }
    return panel
  }

  override fun apply() {
    if (panel.validateAll().isNotEmpty()) return
    panel.apply()
  }

  private fun validateOAuthConfig(textArea: JBTextArea): ValidationInfo? {
    textArea.text.lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .forEach { line: @NlsSafe String ->
        try {
          val separatorIndex = line.indexOf('=')
          if (separatorIndex <= 0 || separatorIndex == line.lastIndex) {
            return ValidationInfo(message("settings.oauth.validation.one.per.line") + "\n$line", textArea)
          }
          val serverUri = line.substring(0, separatorIndex).trim()
          val clientId = line.substring(separatorIndex + 1).trim()

          if (clientId.any { it.isWhitespace() }) {
            return ValidationInfo(message("settings.oauth.validation.one.per.line") + "\n$line", textArea)
          }

          if (serverUri.isEmpty() || clientId.isEmpty()) {
            return ValidationInfo(message("settings.oauth.validation.incomplete") + "\n$line", textArea)
          }

          if (!URIUtil.isValidHttpUri(serverUri)) {
            return ValidationInfo(message("settings.oauth.validation.invalid.uri") + "\n$line", textArea)
          }
        }
        catch (_: Exception) {
          return ValidationInfo(message("settings.oauth.validation.incomplete") + "\n$line", textArea)
        }
      }
    return null
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "GitLabSettings", storages = [Storage("gitlab.xml")], category = SettingsCategory.TOOLS)
class GitLabSettings : SerializablePersistentStateComponent<GitLabSettings.State>(State()) {
  @Serializable
  data class State(
    val isAutomaticallyMarkAsViewed: Boolean = false,
    val isCloneGitUsingSsh: Boolean = false,
  )

  var isAutomaticallyMarkAsViewed: Boolean
    get() = state.isAutomaticallyMarkAsViewed
    set(value) {
      updateState { it.copy(isAutomaticallyMarkAsViewed = value) }
    }

  var isCloneGitUsingSsh: Boolean
    get() = state.isCloneGitUsingSsh
    set(value) {
      updateState { it.copy(isCloneGitUsingSsh = value) }
    }

  companion object {
    fun getInstance(): GitLabSettings = ApplicationManager.getApplication().service<GitLabSettings>()
  }
}
