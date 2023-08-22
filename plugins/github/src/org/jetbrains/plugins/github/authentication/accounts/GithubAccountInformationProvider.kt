// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.auth.AccountsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import java.io.IOException
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Loads the account information or provides it from cache
 * TODO: more abstraction
 */
class GithubAccountInformationProvider : Disposable {

  private val informationCache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.of(30, ChronoUnit.MINUTES))
    .build<GithubAccount, GithubAuthenticatedUser>()

  init {
    disposingScope().launch {
      service<GHAccountManager>().accountsState.collect {
        informationCache.invalidateAll()
      }
    }
  }

  @RequiresBackgroundThread
  @Throws(IOException::class)
  fun getInformation(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, account: GithubAccount): GithubAuthenticatedUser {
    return informationCache.get(account) { executor.execute(indicator, GithubApiRequests.CurrentUser.get(account.server)) }
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubAccountInformationProvider {
      return service()
    }
  }

  override fun dispose() {
    informationCache.invalidateAll()
  }
}