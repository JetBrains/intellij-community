// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OnboardingBackgroundImageProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.util.SVGLoader
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Image
import java.io.IOException
import java.net.URL

@Internal
abstract class OnboardingBackgroundImageProviderBase : OnboardingBackgroundImageProvider {
  open fun getImageUrl(isDark: Boolean): URL? = null

  override fun getImage(isDark: Boolean): Image? {
    val imageUrl = getImageUrl(isDark)?.takeIf { isAvailable && it.path.endsWith(".svg"); } ?: return null

    val image: Image? = BackgroundTaskUtil.tryComputeFast(
      { progressIndicator ->
        try {
          return@tryComputeFast SVGLoader.load(imageUrl, 1f)
        }
        catch (e: IOException) {
          LOG.warn("Onboarding image loading failed: $e")
          return@tryComputeFast null
        }
        finally {
          if (progressIndicator.isCanceled) {
            LOG.warn("Onboarding image loading failed: it took longer than $LOADING_TIMEOUT_MILLIS ms")
          }
        }
      }, LOADING_TIMEOUT_MILLIS)

    return image
  }

  override fun setBackgroundImageToDialog(dialog: DialogWrapper, image: Image?) {
    var didHaveImage = false

    ClientProperty.get(dialog.rootPane, BACKGROUND_IMAGE_DISPOSABLE_KEY)?.let { previousDisposable ->
      didHaveImage = true
      Disposer.dispose(previousDisposable)
      ClientProperty.remove(dialog.rootPane, BACKGROUND_IMAGE_DISPOSABLE_KEY)
    }

    if (image == null) {
      if (didHaveImage) {
        dialog.rootPane.repaint()
      }
      return
    }

    val disposable = Disposer.newDisposable(dialog.disposable)
    ClientProperty.put(dialog.rootPane, BACKGROUND_IMAGE_DISPOSABLE_KEY, disposable)

    IdeBackgroundUtil.createTemporaryBackgroundTransform(dialog.rootPane,
                                                         image,
                                                         IdeBackgroundUtil.Fill.SCALE,
                                                         IdeBackgroundUtil.Anchor.CENTER,
                                                         1f,
                                                         JBInsets.emptyInsets(),
                                                         disposable)

    dialog.rootPane.repaint()
  }

  override fun hasBackgroundImage(dialog: DialogWrapper): Boolean =
    ClientProperty.get(dialog.rootPane, BACKGROUND_IMAGE_DISPOSABLE_KEY) != null

  companion object {
    private val BACKGROUND_IMAGE_DISPOSABLE_KEY: Key<Disposable> = Key.create("ide.background.image.provider.background.image")
    private const val LOADING_TIMEOUT_MILLIS: Long = 300
    private val LOG = Logger.getInstance(OnboardingBackgroundImageProviderBase::class.java)
  }
}