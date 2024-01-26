// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHReactionContent
import java.awt.Image
import java.net.URL
import javax.imageio.ImageIO

internal class GHReactionImageLoader(
  parentCs: CoroutineScope,
  private val requestExecutor: GithubApiRequestExecutor
) : AsyncImageIconsProvider.AsyncImageLoader<GHReactionContent> {
  private val cs = parentCs.childScope(CoroutineName("GitHub Reactions Image Loader"))

  private val emojisNameToUrl: Deferred<Map<String, String>> = cs.async(Dispatchers.IO) {
    requestExecutor.execute(EmptyProgressIndicator(), GithubApiRequest.Get.JsonMap(GITHUB_EMOJI_API_URL))
  }

  override suspend fun load(key: GHReactionContent): Image? {
    val map = emojisNameToUrl.await()
    val url = map[key.emojiName]?.let { URL(it) } ?: return null
    return withContext(Dispatchers.IO) {
      ImageIO.read(url)
    }
  }

  companion object {
    private const val GITHUB_EMOJI_API_URL = "https://api.github.com/emojis"
  }
}

private val GHReactionContent.emojiName: String
  get() = when (this) {
    GHReactionContent.THUMBS_UP -> "+1"
    GHReactionContent.THUMBS_DOWN -> "-1"
    GHReactionContent.LAUGH -> "smile"
    GHReactionContent.HOORAY -> "tada"
    GHReactionContent.CONFUSED -> "confused"
    GHReactionContent.HEART -> "heart"
    GHReactionContent.ROCKET -> "rocket"
    GHReactionContent.EYES -> "eyes"
  }