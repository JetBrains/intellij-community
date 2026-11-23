// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable

/**
 * Stores the OAuth application client IDs registered for GitLab servers.
 */
@Service(Service.Level.PROJECT)
@State(name = "GitLabOAuthSettings",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "gitlab-oauth.xml")],
       reportStatistic = false)
internal class GitLabOAuthSettings :
  SerializablePersistentStateComponent<GitLabOAuthSettings.GitLabOAuthAppSettingsState>(GitLabOAuthAppSettingsState()) {

  @Serializable
  data class GitLabOAuthAppSettingsState(
    val customServers: List<CustomServer> = emptyList(),
    @Deprecated("Use CustomServer")
    val clientIds: Map<String, String> = emptyMap(),
  )

  override fun loadState(state: GitLabOAuthAppSettingsState) {
    val migrated = state.clientIds.map { [uri, clientId] ->
      CustomServer(uri, clientId, null)
    }
    super.loadState(GitLabOAuthAppSettingsState(state.customServers + migrated))
  }

  var customServers: List<CustomServer>
    get() = state.customServers
    set(value) {
      updateState { it.copy(customServers = value) }
    }

  companion object {
    fun getInstance(project: Project): GitLabOAuthSettings = project.service()
  }
}

@Serializable
internal data class CustomServer(
  val serverUri: String,
  val clientId: String? = null,
  val rootNamespaceId: Int? = null,
)
