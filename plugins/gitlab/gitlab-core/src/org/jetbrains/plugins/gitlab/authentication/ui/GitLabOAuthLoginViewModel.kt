// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.ValidationBinding
import com.intellij.collaboration.ui.util.validationBinding
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.GitHostingUrlUtil
import git4idea.remote.hosting.collectRemotes
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabGitAuthorizationSignal
import org.jetbrains.plugins.gitlab.authentication.GitLabOAuthService
import org.jetbrains.plugins.gitlab.authentication.GitLabOAuthSettings
import org.jetbrains.plugins.gitlab.authentication.GitLabSecurityUtil
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabOAuthLoginOutcome.OtherMethod
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabOAuthLoginOutcome.Success
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabOAuthLoginViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  requiredServerPath: GitLabServerPath?,
  private val requiredUsername: String? = null,
  private val uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
) : GitLabGitAuthorizationSignal {

  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Default)
  val servers: List<String> =
    (GitRepositoryManager.getInstance(project).collectRemotes().mapNotNull { getServerPath(it.url) } +
     GitLabOAuthSettings.getInstance(project).state.clientIds.keys.mapNotNull { getServerPath(it) })
      .filter { !it.isDefault }
      .map { it.uri }
      .distinct()

  private val _serverUri: MutableStateFlow<String> = MutableStateFlow(requiredServerPath?.uri ?: servers.firstOrNull().orEmpty())
  private val _clientId: MutableStateFlow<String> = MutableStateFlow(getStoredClientId(_serverUri.value).orEmpty())

  private val _loginState = MutableStateFlow<LoginState>(LoginState.Disconnected)
  val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

  val isLoggingIn: StateFlow<Boolean> = loginState.mapState { it is LoginState.Connecting }

  private val _outcome = MutableStateFlow<GitLabOAuthLoginOutcome?>(null)
  val outcome: StateFlow<GitLabOAuthLoginOutcome?> = _outcome.asStateFlow()

  private val _tryGitAuthorizationSignal: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1)
  override val tryGitAuthorizationSignal: Flow<Unit> = _tryGitAuthorizationSignal.asSharedFlow()

  val errorFlow: Flow<Throwable?> = loginState.map { state ->
    when (state) {
      is LoginState.Failed -> state.error
      else -> null
    }
  }

  private val serverUriValidationError: StateFlow<@NlsContexts.DialogMessage String?> = _serverUri.mapState { uri ->
    when {
      uri.isBlank() -> CollaborationToolsBundle.message("login.server.empty")
      !URIUtil.isValidHttpUri(uri) -> CollaborationToolsBundle.message("login.server.invalid")
      else -> null
    }
  }

  private val clientIdValidationError: StateFlow<@NlsContexts.DialogMessage String?> = _clientId.mapState { clientId ->
    if (clientId.isEmpty()) GitLabBundle.message("account.oauth.client.id.empty") else null
  }

  val serverUri: ValidationBinding<String> = _serverUri.validationBinding(serverUriValidationError)
  val clientId: ValidationBinding<String> = _clientId.validationBinding(clientIdValidationError)

  init {
    cs.launch {
      _serverUri.collect { uri ->
        val clientId = getStoredClientId(uri) ?: return@collect
        _clientId.value = clientId
      }
    }
    cs.launch {
      tryGitAuthorizationSignal.collect {
        _outcome.value = OtherMethod
      }
    }
  }

  override fun tryGitAuthorization() {
    _tryGitAuthorizationSignal.tryEmit(Unit)
  }

  fun requestLogin() {
    if (_loginState.value is LoginState.Connecting) return
    cs.launch {
      _loginState.value = LoginState.Connecting
      try {
        val serverPath = GitLabServerPath(URIUtil.normalizeAndValidateHttpUri(_serverUri.value))
        val clientId = _clientId.value
        val credentials = GitLabOAuthService.instance.authorize(serverPath, clientId)
        val username =
          GitLabSecurityUtil.validateAndResolveUsername(requiredUsername, serverPath, credentials.accessToken, uniqueAccountPredicate)
        _loginState.value = LoginState.Connected(username)
        _outcome.value = Success(serverPath, credentials, username)
      }
      catch (e: CancellationException) {
        _loginState.value = LoginState.Disconnected
        rethrowControlFlowException(e)
      }
      catch (e: Throwable) {
        _loginState.value = LoginState.Failed(e)
      }
    }
  }

  private fun getStoredClientId(serverUri: String): String? {
    val uri = runCatching { URIUtil.normalizeAndValidateHttpUri(serverUri) }.getOrElse { return null }
    return GitLabOAuthSettings.getInstance(project).state.clientIds[uri]
  }

  private fun getServerPath(remoteUrl: String): GitLabServerPath? {
    val uri = GitHostingUrlUtil.getUriFromRemoteUrl(remoteUrl)?.resolve("/")?.toString() ?: return null
    return runCatching { GitLabServerPath(URIUtil.normalizeAndValidateHttpUri(uri)) }.getOrElse { return null }
  }
}
