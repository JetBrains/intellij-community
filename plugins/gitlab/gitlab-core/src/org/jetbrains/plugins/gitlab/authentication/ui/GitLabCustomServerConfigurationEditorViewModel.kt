// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.ValidationBinding
import com.intellij.collaboration.ui.util.validationBinding
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.CustomServer
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabCustomServerConfigurationEditorViewModel(serverToEdit: CustomServer?) {
  private val _serverUri = MutableStateFlow(serverToEdit?.serverUri.orEmpty())
  private val _clientId = MutableStateFlow(serverToEdit?.clientId.orEmpty())
  private val _rootNamespaceId = MutableStateFlow(serverToEdit?.rootNamespaceId?.toString().orEmpty())

  val isServerGitlabDotCom: StateFlow<Boolean> = _serverUri.mapState {
    runCatching {
      URIUtil.normalizeAndValidateHttpUri(it.trim()) == GitLabServerPath.DEFAULT_SERVER.uri
    }.getOrNull() ?: false
  }
  val isHttpUri: StateFlow<Boolean> = _serverUri.mapState { uri ->
    uri.trim().startsWith(URLUtil.HTTP_PROTOCOL + URLUtil.SCHEME_SEPARATOR)
  }

  private val serverUriError: StateFlow<@NlsContexts.DialogMessage String?> = _serverUri.mapState {
    val uri = it.trim()
    when {
      uri.isEmpty() -> CollaborationToolsBundle.message("login.server.empty")
      !URIUtil.isValidHttpUri(uri) -> CollaborationToolsBundle.message("login.server.invalid")
      else -> null
    }
  }

  private val clientIdError: StateFlow<@NlsContexts.DialogMessage String?> =
    isServerGitlabDotCom.combineState(_clientId) { isServerGitlabDotCom, clientId ->
      if (isServerGitlabDotCom || clientId.isNotBlank()) null
      else GitLabBundle.message("account.oauth.client.id.empty")
    }

  private val rootNamespaceIdError: StateFlow<@NlsContexts.DialogMessage String?> =
    isServerGitlabDotCom.combineState(_rootNamespaceId) { isServerGitlabDotCom, rootNamespaceId ->
      when {
        rootNamespaceId.isBlank() -> {
          if (isServerGitlabDotCom) GitLabBundle.message("settings.oauth.custom.server.root.namespace.id.invalid")
          else null
        }
        parseRootNamespaceId(rootNamespaceId) == null -> GitLabBundle.message("settings.oauth.custom.server.root.namespace.id.invalid")
        else -> null
      }
    }

  val serverUri: ValidationBinding<String> = _serverUri.validationBinding(serverUriError)
  val clientId: ValidationBinding<String> = _clientId.validationBinding(clientIdError)
  val rootNamespaceId: ValidationBinding<String> = _rootNamespaceId.validationBinding(rootNamespaceIdError)

  fun buildServer(): CustomServer? {
    if (serverUriError.value != null || clientIdError.value != null || rootNamespaceIdError.value != null) return null
    val serverUri = URIUtil.normalizeAndValidateHttpUri(_serverUri.value.trim())
    val clientId = _clientId.value.trim().ifEmpty { null }.takeIf { !isServerGitlabDotCom.value }
    val rootNamespaceId = parseRootNamespaceId(_rootNamespaceId.value)
    return CustomServer(serverUri, clientId, rootNamespaceId)
  }

  private fun parseRootNamespaceId(value: String): Int? = value.trim().toIntOrNull()?.takeIf { it > 0 }
}
