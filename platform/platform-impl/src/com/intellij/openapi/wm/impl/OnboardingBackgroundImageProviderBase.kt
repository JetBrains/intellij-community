// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
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
import java.net.URL

@Internal
abstract class OnboardingBackgroundImageProviderBase : OnboardingBackgroundImageProvider {
  open fun getImageUrl(): URL? = null

  override fun getImage(): Image? {
    val imageUrl = getImageUrl()?.takeIf { isAvailable && it.path.endsWith(".svg"); } ?: return null

    var image: Image? = null
    BackgroundTaskUtil.executeAndTryWait(
      { indicator: ProgressIndicator ->
        val loadedImage = SVGLoader.load(imageUrl, 1f)

        Runnable {
          if (indicator.isCanceled) return@Runnable
          image = loadedImage
        }
      },
      {
        LOG.warn("Onboarding image loading failed (> $LOADING_TIMEOUT_MILLIS ms): ")
      },
      LOADING_TIMEOUT_MILLIS,
      false)

    return image
  }

  override fun setBackgroundImageToDialog(dialog: DialogWrapper, image: Image?) {
    ClientProperty.get(dialog.rootPane, BACKGROUND_IMAGE_DISPOSABLE_KEY)?.let { previousDisposable ->
      Disposer.dispose(previousDisposable)
      ClientProperty.remove(dialog.rootPane, BACKGROUND_IMAGE_DISPOSABLE_KEY)
    }

    if (image == null) return

    val disposable = Disposer.newDisposable(dialog.disposable)
    ClientProperty.put(dialog.rootPane, BACKGROUND_IMAGE_DISPOSABLE_KEY, disposable)

    IdeBackgroundUtil.createTemporaryBackgroundTransform(dialog.rootPane,
                                                         image,
                                                         IdeBackgroundUtil.Fill.SCALE,
                                                         IdeBackgroundUtil.Anchor.CENTER,
                                                         1f,
                                                         JBInsets.emptyInsets(),
                                                         disposable)
  }

  companion object {
    private val BACKGROUND_IMAGE_DISPOSABLE_KEY: Key<Disposable> = Key.create("ide.background.image.provider.background.image")
    private const val LOADING_TIMEOUT_MILLIS: Long = 300
    private val LOG = Logger.getInstance(OnboardingBackgroundImageProviderBase::class.java)
  }
}