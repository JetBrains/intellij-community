// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.util.ThrowableConvertor
import org.jetbrains.plugins.github.api.GithubApiRequest.Get
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import java.awt.Image

/**
 * Collection of factory methods for API requests used in plugin
 * TODO: improve url building (DSL?)
 */
object GithubApiRequests {
  object CurrentUser : Entity("/user") {
    @JvmStatic
    fun get(server: GithubServerPath) = get(getUrl(server, urlSuffix))

    @JvmStatic
    fun get(url: String) = Get.json<GithubAuthenticatedUser>(url).withOperationName("get profile information")

    @JvmStatic
    fun getAvatar(url: String) = object : Get<Image>(url) {
      override fun extractResult(response: GithubApiResponse): Image {
        return response.handleBody(ThrowableConvertor {
          GithubApiContentHelper.loadImage(it)
        })
      }
    }.withOperationName("get profile avatar")
  }

  abstract class Entity(val urlSuffix: String)

  private fun getUrl(server: GithubServerPath, suffix: String) = server.toApiUrl() + suffix
}