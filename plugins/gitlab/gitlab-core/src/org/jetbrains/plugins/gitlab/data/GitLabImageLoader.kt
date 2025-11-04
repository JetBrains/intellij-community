// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.data

import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.loadImage
import java.awt.Image
import java.net.URI
import java.net.URL
import javax.swing.Icon
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<GitLabImageLoader>()

private const val LOADED_GRAVATAR_SIZE: Int = 80

class GitLabImageLoader(
  private val apiClient: GitLabApi,
  private val gitLabProjectCoordinates: GitLabProjectCoordinates
) : AsyncImageIconsProvider.AsyncImageLoader<GitLabUserDTO>, AsyncHtmlImageLoader {
  override suspend fun load(key: GitLabUserDTO): Image? {
    return key.avatarUrl?.let { avatarUrl ->
      val actualUri = when {
        avatarUrl.startsWith("http") -> avatarUrl
        avatarUrl.startsWith("/avatar") -> "https://secure.gravatar.com$avatarUrl?s=$LOADED_GRAVATAR_SIZE&d=identicon"
        else -> gitLabProjectCoordinates.serverPath.uri + avatarUrl
      }
      load(null, actualUri)
    }
  }

  override suspend fun load(baseUrl: URL?, src: String): Image? {
    try {
      val uri = URI.create(src)
      return apiClient.loadImage(uri)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.warn("Failed to load the image from src $src", e)
      throw e
    }
  }

  override fun createBaseIcon(key: GitLabUserDTO?, iconSize: Int): Icon {
    return IconUtil.resizeSquared(CollaborationToolsIcons.Review.DefaultAvatar, iconSize)
  }

  override suspend fun postProcess(image: Image): Image {
    return ImageUtil.createCircleImage(ImageUtil.toBufferedImage(image))
  }
}
