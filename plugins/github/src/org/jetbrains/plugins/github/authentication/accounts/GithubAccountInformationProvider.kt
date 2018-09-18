// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import java.awt.Image
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Loads the account information or provides it from cache
 * TODO: more abstraction
 */
class GithubAccountInformationProvider {

  private val informationCache = CacheBuilder.newBuilder()
    .expireAfterAccess(30, TimeUnit.MINUTES)
    .build<GithubAccount, GithubUserDetailed>()
    .asMap()

  private val avatarCache = CacheBuilder.newBuilder()
    .expireAfterAccess(30, TimeUnit.MINUTES)
    .build<GithubAccount, Image>()
    .asMap()

  init {
    ApplicationManager.getApplication().messageBus
      .connect()
      .subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
        override fun tokenChanged(account: GithubAccount) {
          informationCache.remove(account)
          avatarCache.remove(account)
        }
      })
  }

  @CalledInBackground
  @Throws(IOException::class)
  fun getInformation(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, account: GithubAccount): GithubUserDetailed {
    return informationCache.getOrPut(account) { executor.execute(indicator, GithubApiRequests.CurrentUser.get(account.server)) }
  }

  @CalledInBackground
  @Throws(IOException::class)
  fun getAvatar(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, account: GithubAccount, url: String): Image {
    return avatarCache.getOrPut(account) { executor.execute(indicator, GithubApiRequests.CurrentUser.getAvatar(url)) }
  }
}