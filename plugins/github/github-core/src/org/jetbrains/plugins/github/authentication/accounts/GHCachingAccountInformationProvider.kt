// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.accounts

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.executeSuspend
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Loads the account information or provides it from cache
 */
@Service(Service.Level.APP)
internal class GHCachingAccountInformationProvider(serviceCs: CoroutineScope) {
  private val cs = serviceCs.childScope()

  private val informationCache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.of(30, ChronoUnit.MINUTES))
    .build<GithubAccount, Deferred<GithubAuthenticatedUser>>()

  init {
    cs.launch {
      service<GHAccountManager>().accountsState.collect {
        informationCache.invalidateAll()
      }
    }
  }

  /**
   * Will either schedule loading with the supplied executor or await the running request
   */
  suspend fun loadInformation(executor: GithubApiRequestExecutor, account: GithubAccount): GithubAuthenticatedUser =
    informationCache.get(account) {
      cs.async {
        executor.executeSuspend(GithubApiRequests.CurrentUser.get(account.server))
      }
    }.await()

  companion object {
    @JvmStatic
    fun getInstance(): GHCachingAccountInformationProvider = service()
  }
}