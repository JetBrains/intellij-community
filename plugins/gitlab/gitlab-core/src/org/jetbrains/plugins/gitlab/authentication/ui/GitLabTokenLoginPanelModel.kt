// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.login.LoginPanelModelBase
import com.intellij.collaboration.auth.ui.login.LoginTokenGenerator
import com.intellij.collaboration.util.URIUtil
import com.intellij.ide.BrowserUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabGitAuthorizationSignal
import org.jetbrains.plugins.gitlab.authentication.GitLabSecurityUtil

class GitLabTokenLoginPanelModel(
  var requiredUsername: String? = null,
  var uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
) : LoginPanelModelBase(), LoginTokenGenerator, GitLabGitAuthorizationSignal {
  private val _tryGitAuthorizationSignal: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1)
  override val tryGitAuthorizationSignal: Flow<Unit> = _tryGitAuthorizationSignal.asSharedFlow()

  override suspend fun checkToken(): String {
    val server = createServerPath(serverUri)
    return GitLabSecurityUtil.validateAndResolveUsername(requiredUsername, server, token, uniqueAccountPredicate)
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

  override fun tryGitAuthorization() {
    _tryGitAuthorizationSignal.tryEmit(Unit)
  }
}