// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OnboardingBackgroundImageProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.util.SVGLoader
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Image
import java.util.function.Consumer

@Internal
abstract class OnboardingBackgroundImageProviderBase : OnboardingBackgroundImageProvider {
  override fun loadImage(callback: Consumer<Image?>) {
    loadImage { image -> callback.accept(image) }
  }

  private fun loadImage(callback: (Image?) -> Unit) {
    val imageUrl = getImageUrl()?.takeIf { isAvailable && it.path.endsWith(".svg"); }
    if (imageUrl == null) {
      callback(null)
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      val image = SVGLoader.load(imageUrl, 1f)
      ApplicationManager.getApplication().invokeLater {
        callback(image)
      }
    }
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
  }
}