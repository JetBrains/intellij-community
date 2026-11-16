// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import com.intellij.collaboration.ui.util.ValidationBinding
import com.intellij.collaboration.ui.util.validationBinding
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.i18n.GithubBundle.message

internal typealias UniqueLoginPredicate = (login: String, server: GithubServerPath) -> Boolean

/**
 * The view model of the token login form that [GHTokenLoginPanelFactory] creates.
 */
internal class GHTokenLoginViewModel(
  parentCs: CoroutineScope,
  private val executorFactory: GithubApiRequestExecutor.Factory,
  server: String = GithubServerPath.DEFAULT_HOST,
  private val fixedLogin: String? = null,
  private val isAccountUnique: UniqueLoginPredicate,
  private val saveLogin: suspend (server: GithubServerPath, login: String, token: String) -> Unit,
) {
  private val cs = parentCs.childScope(javaClass.name)
  private val loginLauncher = SingleCoroutineLauncher(cs)

  private val _server = MutableStateFlow(server)
  private val _token = MutableStateFlow("")

  val serverPath: StateFlow<GithubServerPath?> = _server.mapState(::tryParseServer)

  val server: ValidationBinding<String> = _server.validationBinding(_server.mapState(::validateServer))
  val token: ValidationBinding<String> = _token.validationBinding(_token.mapState(::validateToken))

  private val _loginState = MutableStateFlow<LoginState>(LoginState.Disconnected)
  val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

  val isLoggingIn: StateFlow<Boolean> = _loginState.mapState { it is LoginState.Connecting }

  val errorFlow: StateFlow<Throwable?> = loginState.mapState { state ->
    when (state) {
      is LoginState.Failed -> state.error
      else -> null
    }
  }

  fun requestLogin() {
    loginLauncher.launch {
      _loginState.value = LoginState.Connecting
      try {
        val server = GithubServerPath.from(_server.value.trim())
        val token = _token.value
        val executor = executorFactory.create(server, token)
        val login = GHAccountsUtil.acquireLogin(server, executor, isAccountUnique, fixedLogin)
        saveLogin(server, login, token)
        _loginState.value = LoginState.Connected(login)
      }
      catch (e: CancellationException) {
        _loginState.value = LoginState.Disconnected
        rethrowControlFlowException(e)
      }
      catch (e: Exception) {
        _loginState.value = LoginState.Failed(e)
      }
    }
  }

  fun cancelLogin() {
    loginLauncher.cancel()
  }
}

private fun tryParseServer(server: String): GithubServerPath? =
  try {
    GithubServerPath.from(server.trim())
  }
  catch (_: GithubParseException) {
    null
  }

private fun validateServer(server: String): @NlsContexts.DialogMessage String? =
  when {
    server.isBlank() -> message("credentials.server.cannot.be.empty")
    tryParseServer(server) == null -> message("credentials.server.path.invalid")
    else -> null
  }

private fun validateToken(token: String): @NlsContexts.DialogMessage String? =
  if (token.isBlank()) message("login.token.cannot.be.empty") else null
