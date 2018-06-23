// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GithubApiUtil
import org.jetbrains.plugins.github.api.GithubConnection
import org.jetbrains.plugins.github.api.GithubTask
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import java.io.IOException
import java.util.concurrent.TimeUnit

//TODO: load image
class GithubAccountInformationProvider {

  private val informationCache = CacheBuilder.newBuilder()
    .expireAfterAccess(30, TimeUnit.MINUTES)
    .build<GithubAccount, GithubUserDetailed>()
    .asMap()

  init {
    ApplicationManager.getApplication().messageBus
      .connect()
      .subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
        override fun tokenChanged(account: GithubAccount) {
          informationCache.remove(account)
        }
      })
  }

  @CalledInBackground
  @Throws(IOException::class)
  fun getAccountInformation(connection: GithubConnection): GithubUserDetailed {
    val account = connection.account
    return if (account == null) GithubApiUtil.getCurrentUser(connection)
    else informationCache.getOrPut(account) { GithubApiUtil.getCurrentUser(connection) }
  }

  val informationTask get() = GithubTask<GithubUserDetailed> { getAccountInformation(it) }

  @CalledInBackground
  @Throws(IOException::class)
  fun getAccountUsername(connection: GithubConnection) = getAccountInformation(connection).login

  val usernameTask get() = GithubTask<String> { getAccountUsername(it) }
}