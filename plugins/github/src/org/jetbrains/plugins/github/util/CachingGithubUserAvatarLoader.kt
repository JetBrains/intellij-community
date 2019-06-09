// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.util.ImageLoader
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import java.awt.Image
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class CachingGithubUserAvatarLoader(private val progressManager: ProgressManager) : Disposable {
  private val LOG = logger<CachingGithubUserAvatarLoader>()

  private val executor = AppExecutorUtil.getAppExecutorService()
  private val progressIndicator: EmptyProgressIndicator = NonReusableEmptyProgressIndicator()

  private val avatarCache = CacheBuilder.newBuilder()
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build<String, CompletableFuture<Image?>>()

  init {
    LowMemoryWatcher.register(Runnable { avatarCache.invalidateAll() }, this)
  }

  fun requestAvatar(requestExecutor: GithubApiRequestExecutor, url: String): CompletableFuture<Image?> {
    val indicator = progressIndicator
    // store images at maximum used size with maximum reasonable scale to avoid upscaling (3 for system scale, 2 for user scale)
    val imageSize = MAXIMUM_ICON_SIZE * 6

    return avatarCache.get(url) {
      CompletableFuture.supplyAsync(Supplier {
        try {
          progressManager.runProcess(Computable { loadAndDownscale(requestExecutor, indicator, url, imageSize) }, indicator)
        }
        catch (e: ProcessCanceledException) {
          null
        }
      }, executor)
    }
  }

  private fun loadAndDownscale(requestExecutor: GithubApiRequestExecutor, indicator: EmptyProgressIndicator,
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

  override fun dispose() {
    progressIndicator.cancel()
  }

  companion object {
    private const val MAXIMUM_ICON_SIZE = 40
  }
}