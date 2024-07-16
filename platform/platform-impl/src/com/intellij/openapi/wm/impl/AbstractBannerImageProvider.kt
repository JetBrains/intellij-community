// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.ui.BannerImageProvider
import com.intellij.ui.svg.loadCustomImage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Dimension
import java.awt.Image
import java.io.IOException
import java.net.URL

private const val TIMEOUT = 500L
private val LOG = Logger.getInstance(OnboardingBackgroundImageProviderBase::class.java)

@ApiStatus.Internal
abstract class AbstractBannerImageProvider: BannerImageProvider {

  protected open fun getImageUrl(): URL? = null

  override fun getIDEBanner(size: Dimension): Image? {
    return getImageUrl()?.let { url ->
      BackgroundTaskUtil.tryComputeFast(
        { progressIndicator ->
          try {
            return@tryComputeFast url.openStream().readAllBytes()?.let { loadCustomImage(size, it) }
          }
          catch (e: IOException) {
            LOG.warn("Onboarding image loading failed: $e")
            return@tryComputeFast null
          }
          finally {
            if (progressIndicator.isCanceled) {
              LOG.warn("Onboarding image loading failed: it took longer than $TIMEOUT ms")
            }
          }
        }, TIMEOUT)
    }

  }
}