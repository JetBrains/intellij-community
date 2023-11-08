// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.collaboration.util.serviceGet
import com.intellij.openapi.components.Service
import com.intellij.util.childScope
import git4idea.remote.hosting.http.HostedGitAuthenticationFailureManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

@Service(Service.Level.PROJECT)
internal class GHGitAuthenticationFailureManager(parentCs: CoroutineScope)
  : HostedGitAuthenticationFailureManager<GithubAccount>(serviceGet<GHAccountManager>(), parentCs.childScope())