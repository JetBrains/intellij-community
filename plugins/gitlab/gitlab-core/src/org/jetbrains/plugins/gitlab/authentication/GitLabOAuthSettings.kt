// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable

private val LOG = logger<GitLabOAuthSettings>()

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
    val clientIds: Map<String, String> = emptyMap(),
  )

  var clientIds: Map<String, String>
    get() = state.clientIds
    set(value) {
      updateState { it.copy(clientIds = value) }
    }

  var clientIdsText: String
    get() = clientIds.entries
      .sortedBy { it.key }
      .joinToString("\n") { (server, clientId) -> "$server=$clientId" }
    set(value) {
      clientIds = parseClientIds(value)
    }

  companion object {
    fun getInstance(project: Project): GitLabOAuthSettings = project.service()
  }
}

private fun parseClientIds(text: String): Map<String, String> =
  text.lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapNotNull { line ->
      try {
        val separatorIndex = line.indexOf('=')
        if (separatorIndex <= 0 || separatorIndex == line.lastIndex) {
          LOG.warn("Ignoring malformed GitLab OAuth client ID configuration entry, should be serverURI=clientID pair: $line")
          return@mapNotNull null
        }

        val server = line.substring(0, separatorIndex).trim()
        val clientId = line.substring(separatorIndex + 1).trim()
        if (server.isEmpty() || clientId.isEmpty()) {
          LOG.warn("Ignoring malformed GitLab OAuth client ID configuration entry, should be serverURI=clientID pair: $line")
          return@mapNotNull null
        }

        val serverUri = try {
          URIUtil.normalizeAndValidateHttpUri(server)
        }
        catch (_: Exception) {
          LOG.warn("Ignoring malformed GitLab OAuth client ID configuration entry - invalid URI, should be serverURI=clientID pair: $line")
          return@mapNotNull null
        }

        serverUri to clientId
      }
      catch (_: Exception) {
        return@mapNotNull null
      }
    }
    .toMap()