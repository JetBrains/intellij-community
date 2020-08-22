// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.ui.ImageUtil
import java.awt.Image
import java.util.concurrent.CompletableFuture

class GithubImageResizer : Disposable {
  private val indicatorProvider = ProgressIndicatorsProvider().also {
    Disposer.register(this, it)
  }

  fun requestImageResize(image: Image, size: Int, scaleContext: ScaleContext): CompletableFuture<Image> =
    ProgressManager.getInstance().submitIOTask(indicatorProvider) {
      it.checkCanceled()
      val hidpiImage = ImageUtil.ensureHiDPI(image, scaleContext)
      it.checkCanceled()
      ImageLoader.scaleImage(hidpiImage, size)
    }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): GithubImageResizer = service()
  }
}
