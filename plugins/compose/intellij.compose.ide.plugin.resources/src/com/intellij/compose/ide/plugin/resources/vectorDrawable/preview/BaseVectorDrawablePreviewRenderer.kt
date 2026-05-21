// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.math.min

/**
 * Extension point for rendering vector-drawable XML files to images.
 * The Android implementation uses VdPreview when the Android plugin is available,
 * otherwise a fallback implementation is used.
 */
abstract class BaseVectorDrawablePreviewRenderer {
  fun renderPreview(xmlContent: String, viewportWidth: Int, viewportHeight: Int): RenderResult {
    val dimension = getVectorDrawableSizeDp(xmlContent)
                    ?: return RenderResult.Error("Invalid vector drawable: missing width/height")

    var imageScale = 1.0
    if (dimension.width > 0 && dimension.height > 0) {
      val scaleX = viewportWidth.toDouble() / dimension.width
      val scaleY = viewportHeight.toDouble() / dimension.height
      imageScale = min(scaleX, scaleY)
    }
    val errors = StringBuilder()
    val image = doRenderPreview(imageScale, xmlContent, errors)

    return when {
      errors.isNotEmpty() -> RenderResult.Error(errors.toString())
      image == null -> RenderResult.Error("Failed to render preview")
      else -> RenderResult.Success(image)
    }
  }

  abstract fun getVectorDrawableSizeDp(xmlContent: String): Dimension?

  abstract fun doRenderPreview(imageScale: Double, xmlContent: String, errors: StringBuilder): BufferedImage?

  sealed class RenderResult {
    data class Success(val image: BufferedImage) : RenderResult()
    data class Error(val message: String) : RenderResult()
  }

  companion object {
    private val EP_NAME: ExtensionPointName<BaseVectorDrawablePreviewRenderer> =
      ExtensionPointName.create("com.intellij.compose.ide.plugin.resources.drawablePreviewRenderer")

    fun getInstance(): BaseVectorDrawablePreviewRenderer? =
      EP_NAME.extensionList.firstOrNull()
  }
}