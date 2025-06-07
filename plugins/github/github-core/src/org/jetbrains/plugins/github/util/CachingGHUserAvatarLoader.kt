// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.executeSuspend
import java.awt.Image
import java.awt.image.BufferedImage
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

@Service
class CachingGHUserAvatarLoader internal constructor(serviceCs: CoroutineScope) {
  private val cs = serviceCs.childScope()

  private val avatarCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(5, ChronoUnit.MINUTES))
    .build<String, Deferred<Image?>>()

  init {
    LowMemoryWatcher.register(Runnable { avatarCache.invalidateAll() }, cs.nestedDisposable())
  }

  @Obsolete
  fun requestAvatar(requestExecutor: GithubApiRequestExecutor, url: String): CompletableFuture<Image?> =
    cs.async { loadAvatar(requestExecutor, url) }.asCompletableFuture()

  suspend fun loadAvatar(requestExecutor: GithubApiRequestExecutor, url: String): Image? =
    avatarCache.get(url) {
      cs.async {
        loadAndDownscale(requestExecutor, url, STORED_IMAGE_SIZE)
      }
    }.await()

  private suspend fun loadAndDownscale(requestExecutor: GithubApiRequestExecutor, url: String, maximumSize: Int): Image? {
    try {
      val loadedImage = requestExecutor.executeSuspend(GithubApiRequests.CurrentUser.getAvatar(url))
      return if (loadedImage.width <= maximumSize && loadedImage.height <= maximumSize) loadedImage
      else ImageLoader.scaleImage(loadedImage, maximumSize) as BufferedImage
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.debug("Error loading image from $url", e)
      return null
    }
  }

  companion object {
    private val LOG = logger<CachingGHUserAvatarLoader>()

    @JvmStatic
    fun getInstance(): CachingGHUserAvatarLoader = service()

    private const val MAXIMUM_ICON_SIZE = 40

    // store images at maximum used size with maximum reasonable scale to avoid upscaling (3 for system scale, 2 for user scale)
    private const val STORED_IMAGE_SIZE = MAXIMUM_ICON_SIZE * 6
  }
}