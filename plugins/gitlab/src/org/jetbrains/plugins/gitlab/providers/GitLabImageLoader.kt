// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.providers

import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.loadImage
import java.awt.Image
import javax.swing.Icon

internal class GitLabImageLoader(
  private val apiClient: GitLabApi,
  private val server: GitLabServerPath
) : AsyncImageIconsProvider.AsyncImageLoader<GitLabUserDTO> {
  override suspend fun load(key: GitLabUserDTO): Image? {
    return key.avatarUrl?.let { avatarUrl ->
      val actualUrl = if (avatarUrl.startsWith(server.uri)) avatarUrl else server.uri + avatarUrl
      apiClient.loadImage(actualUrl)
    }
  }

  override fun createBaseIcon(key: GitLabUserDTO?, iconSize: Int): Icon {
    return IconUtil.resizeSquared(CollaborationToolsIcons.Review.DefaultAvatar, iconSize)
  }

  override suspend fun postProcess(image: Image): Image {
    return ImageUtil.createCircleImage(ImageUtil.toBufferedImage(image))
  }
}