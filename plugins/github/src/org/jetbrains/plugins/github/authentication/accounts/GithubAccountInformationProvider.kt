// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubApiResponse
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import java.awt.Image
import java.util.concurrent.TimeUnit

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

  @CalledInAny
  fun getInformationRequest(account: GithubAccount): GithubApiRequest<GithubUserDetailed> {
    val delegate = GithubApiRequests.CurrentUser.get(account.server)
    return object : GithubApiRequest.Get<GithubUserDetailed>(delegate.url, delegate.acceptMimeType) {
      override fun extractResult(response: GithubApiResponse): GithubUserDetailed {
        return informationCache.getOrPut(account) { delegate.extractResult(response) }
      }
    }.apply { delegate.operationName?.let(::withOperationName) }
  }

  @CalledInAny
  fun getAvatarRequest(account: GithubAccount, url: String): GithubApiRequest<Image> {
    val delegate = GithubApiRequests.CurrentUser.getAvatar(url)
    return object : GithubApiRequest.Get<Image>(url, delegate.acceptMimeType) {
      override fun extractResult(response: GithubApiResponse): Image {
        return avatarCache.getOrPut(account) { delegate.extractResult(response) }
      }
    }.apply { delegate.operationName?.let(::withOperationName) }
  }
}