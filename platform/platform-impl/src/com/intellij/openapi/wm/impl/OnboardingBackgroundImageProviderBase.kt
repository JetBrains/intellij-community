// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OnboardingBackgroundImageProvider
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

  override fun installBackgroundImageToDialog(dialog: DialogWrapper, image: Image, disposable: Disposable) {
    IdeBackgroundUtil.createTemporaryBackgroundTransform(dialog.rootPane,
                                                         image,
                                                         IdeBackgroundUtil.Fill.SCALE,
                                                         IdeBackgroundUtil.Anchor.CENTER,
                                                         1f,
                                                         JBInsets.emptyInsets(),
                                                         disposable)
  }
}