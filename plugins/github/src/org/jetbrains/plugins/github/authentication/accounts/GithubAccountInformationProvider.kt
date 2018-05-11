// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiTaskExecutor
import org.jetbrains.plugins.github.api.GithubApiUtil
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.GithubTask
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import java.awt.Image
import java.io.IOException
import java.net.URL
import javax.imageio.ImageIO

//TODO: caching
//TODO: load image with GithubApiTaskExecutor
class GithubAccountInformationProvider(private val apiTaskExecutor: GithubApiTaskExecutor) {
  @Throws(IOException::class)
  fun getAccountInformationWithPicture(indicator: ProgressIndicator,
                                       server: GithubServerPath,
                                       token: String): Pair<GithubUserDetailed, Image> {
    val details = GithubApiTaskExecutor.execute(indicator, server, token, GithubTask { c -> GithubApiUtil.getCurrentUser(c) })
    return details to ImageIO.read(URL(details.avatarUrl))
  }

  @Throws(IOException::class)
  fun getAccountInformationWithPicture(indicator: ProgressIndicator, account: GithubAccount): Pair<GithubUserDetailed, Image> {
    val details = getAccountInformation(indicator, account)
    return details to ImageIO.read(URL(details.avatarUrl))
  }

  @Throws(IOException::class)
  fun getAccountInformation(indicator: ProgressIndicator, account: GithubAccount): GithubUserDetailed {
    return apiTaskExecutor.execute(indicator, account, GithubTask { c -> GithubApiUtil.getCurrentUser(c) })
  }

  @Throws(IOException::class)
  fun getAccountUsername(indicator: ProgressIndicator, account: GithubAccount) = getAccountInformation(indicator, account).login
}