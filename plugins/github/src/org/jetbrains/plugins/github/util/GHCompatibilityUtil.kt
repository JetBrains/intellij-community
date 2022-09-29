// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

/**
 * Temporary util methods for maintaining compatibility
 */
object GHCompatibilityUtil {
  @JvmStatic
  fun requestNewAccountForServer(authManager: GithubAuthenticationManager, serverPath: GithubServerPath, project: Project): GithubAccount? =
    authManager.requestNewAccountForServer(serverPath, login = null, project = project)?.account

  @RequiresBackgroundThread
  @JvmStatic
  fun getOrRequestToken(authManager: GithubAuthenticationManager, account: GithubAccount, project: Project): String? {
    val modality = ProgressManager.getInstance().currentProgressModality ?: ModalityState.any()
    return runBlocking {
      authManager.getTokenForAccount(account)
      ?: withContext(Dispatchers.EDT + modality.asContextElement()) {
        authManager.requestNewToken(account, project)
      }
    }
  }
}