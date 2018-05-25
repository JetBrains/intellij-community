// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

//TODO: load image with GithubApiTaskExecutor
class GithubAccountInformationProvider(private val apiTaskExecutor: GithubApiTaskExecutor) {
  private val cacheBuilder = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES)

  private val informationCache = cacheBuilder.build<GithubAccount, GithubAuthenticatedUser>().asMap()
  private val imageCache = cacheBuilder.build<GithubAccount, BufferedImage>().asMap()

  init {
    ApplicationManager.getApplication().messageBus
      .connect()
      .subscribe(GithubAccountManager.ACCOUNT_REMOVED_TOPIC, object : AccountRemovedListener {
        override fun accountRemoved(removedAccount: GithubAccount) {
          informationCache.remove(removedAccount)
          imageCache.remove(removedAccount)
        }
      })
  }

  @Throws(IOException::class)
  fun getAccountInformationWithPicture(indicator: ProgressIndicator,
                                       server: GithubServerPath,
                                       token: String): Pair<GithubAuthenticatedUser, Image> {
    val details = GithubApiTaskExecutor.execute(indicator, server, token, GithubTask { c -> GithubApiUtil.getCurrentUser(c) })
    return details to ImageIO.read(URL(details.avatarUrl))
  }

  @Throws(IOException::class)
  fun getAccountInformationWithPicture(indicator: ProgressIndicator, account: GithubAccount): Pair<GithubAuthenticatedUser, Image> {
    val details = getAccountInformation(indicator, account)
    return details to imageCache.getOrPut(account) { ImageIO.read(URL(details.avatarUrl)) }
  }

  @Throws(IOException::class)
  fun getAccountInformation(indicator: ProgressIndicator, account: GithubAccount): GithubAuthenticatedUser {
    return informationCache.getOrPut(account) {
      apiTaskExecutor.execute(indicator, account, GithubTask { c -> GithubApiUtil.getCurrentUser(c) })
    }
  }

  @Throws(IOException::class)
  fun getAccountInformation(account: GithubAccount, c: GithubConnection): GithubAuthenticatedUser {
    return informationCache.getOrPut(account) { GithubApiUtil.getCurrentUser(c) }
  }

  @Throws(IOException::class)
  fun getAccountUsername(indicator: ProgressIndicator, account: GithubAccount) = getAccountInformation(indicator, account).login
}