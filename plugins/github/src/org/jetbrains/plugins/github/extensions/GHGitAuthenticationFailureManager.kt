// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import git4idea.remote.hosting.http.HostedGitAuthenticationFailureManager
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

@Service(Service.Level.PROJECT)
internal class GHGitAuthenticationFailureManager
  : HostedGitAuthenticationFailureManager<GithubAccount>(accountManager = { service<GHAccountManager>() }) {
  override fun dispose() = Unit
}