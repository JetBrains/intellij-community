// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.login.LoginException
import com.intellij.collaboration.auth.ui.login.LoginPanelModelBase
import com.intellij.collaboration.auth.ui.login.LoginTokenGenerator
import com.intellij.collaboration.util.URIUtil
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.getMetadataOrNull
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.GitLabSecurityUtil

class GitLabTokenLoginPanelModel(var requiredUsername: String? = null,
                                 var uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean)
  : LoginPanelModelBase(), LoginTokenGenerator {

  private val _tryGitAuthorizationSignal: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1)
  val tryGitAuthorizationSignal: Flow<Unit> = _tryGitAuthorizationSignal.asSharedFlow()

  override suspend fun checkToken(): String {
    val server = createServerPath(serverUri)
    val api = service<GitLabApiManager>().getClient(server, token)
    val version = api.getMetadataOrNull()?.version
    val earliestSupportedVersion = serviceAsync<GitLabServersManager>().earliestSupportedVersion

    if (version == null) {
      throw LoginException.InvalidTokenOrUnsupportedServerVersion(earliestSupportedVersion.toString())
    }
    if (version < earliestSupportedVersion) {
      throw LoginException.UnsupportedServerVersion(earliestSupportedVersion.toString())
    }

    val user = withContext(Dispatchers.IO) {
      api.graphQL.getCurrentUser()
    }
    val username = user.username
    val _requiredUsername = requiredUsername
    if (_requiredUsername != null && username != _requiredUsername) {
      throw LoginException.AccountUsernameMismatch(_requiredUsername, username)
    }

    if (!uniqueAccountPredicate(server, username)) {
      throw LoginException.AccountAlreadyExists(username)
    }

    return username
  }

  fun getServerPath(): GitLabServerPath = createServerPath(serverUri)

  private fun createServerPath(uri: String): GitLabServerPath {
    val normalized = URIUtil.normalizeAndValidateHttpUri(uri)
    return GitLabServerPath(normalized)
  }

  override fun canGenerateToken(serverUri: String): Boolean {
    return URIUtil.isValidHttpUri(serverUri)
  }

  override fun generateToken(serverUri: String) {
    val newTokenUrl = GitLabSecurityUtil.buildNewTokenUrl(serverUri) ?: return
    BrowserUtil.browse(newTokenUrl)
  }

  suspend fun tryGitAuthorization() {
    _tryGitAuthorizationSignal.emit(Unit)
  }
}