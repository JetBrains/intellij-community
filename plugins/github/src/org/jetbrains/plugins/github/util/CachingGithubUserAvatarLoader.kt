// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.ImageLoader
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import java.awt.Image
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class CachingGithubUserAvatarLoader : Disposable {
  private val LOG = logger<CachingGithubUserAvatarLoader>()

  private val indicatorProvider = ProgressIndicatorsProvider().also {
    Disposer.register(this, it)
  }

  private val avatarCache = CacheBuilder.newBuilder()
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build<String, CompletableFuture<Image?>>()

  init {
    LowMemoryWatcher.register(Runnable { avatarCache.invalidateAll() }, this)
  }

  fun requestAvatar(requestExecutor: GithubApiRequestExecutor, url: String): CompletableFuture<Image?> = avatarCache.get(url) {
    ProgressManager.getInstance().submitIOTask(indicatorProvider) { loadAndDownscale(requestExecutor, it, url, STORED_IMAGE_SIZE) }
  }

  private fun loadAndDownscale(requestExecutor: GithubApiRequestExecutor, indicator: ProgressIndicator,
                               url: String, maximumSize: Int): Image? {
    try {
      val image = requestExecutor.execute(indicator, GithubApiRequests.CurrentUser.getAvatar(url))
      return if (image.getWidth(null) <= maximumSize && image.getHeight(null) <= maximumSize) image
      else ImageLoader.scaleImage(image, maximumSize)
    }
    catch (e: ProcessCanceledException) {
      return null
    }
    catch (e: Exception) {
      LOG.debug("Error loading image from $url", e)
      return null
    }
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): CachingGithubUserAvatarLoader = service()

    private const val MAXIMUM_ICON_SIZE = 40

    // store images at maximum used size with maximum reasonable scale to avoid upscaling (3 for system scale, 2 for user scale)
    private const val STORED_IMAGE_SIZE = MAXIMUM_ICON_SIZE * 6
  }
}