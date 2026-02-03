// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

/**
 * Temporary util methods for maintaining compatibility
 */
object GHCompatibilityUtil {
  @JvmStatic
  fun requestNewAccountForServer(serverPath: GithubServerPath, project: Project, loginSource: GHLoginSource): GithubAccount? =
    GHAccountsUtil.requestNewAccount(serverPath, login = null, project = project, loginSource = loginSource)?.account

  @RequiresBackgroundThread
  @JvmStatic
  @JvmOverloads
  fun getOrRequestToken(account: GithubAccount, project: Project, loginSource: GHLoginSource = GHLoginSource.UNKNOWN): String? {
    val accountManager = service<GHAccountManager>()
    val modality = ProgressManager.getInstance().currentProgressModality ?: ModalityState.any()
    return runBlocking {
      accountManager.findCredentials(account)
      ?: withContext(Dispatchers.EDT + modality.asContextElement()) {
        GHAccountsUtil.requestNewToken(account, project, loginSource = loginSource)
      }
    }
  }
}