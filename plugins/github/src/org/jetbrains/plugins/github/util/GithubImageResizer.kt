// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil
import java.awt.Image
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class GithubImageResizer(private val progressManager: ProgressManager) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("GitHub Image Resizer", getThreadPoolSize())
  private val progressIndicator: EmptyProgressIndicator = NonReusableEmptyProgressIndicator()

  fun requestImageResize(image: Image, size: Int, scaleContext: ScaleContext): CompletableFuture<Image> {
    val indicator = progressIndicator

    return CompletableFuture.supplyAsync(Supplier {
      progressManager.runProcess(Computable {
        indicator.checkCanceled()
        val hidpiImage = ImageUtil.ensureHiDPI(image, scaleContext)
        indicator.checkCanceled()
        ImageLoader.scaleImage(hidpiImage, size)
      }, indicator)
    }, executor)
  }

  override fun dispose() {
    progressIndicator.cancel()
    executor.shutdownNow()
  }

  companion object {
    private fun getThreadPoolSize() = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1)
  }
}
