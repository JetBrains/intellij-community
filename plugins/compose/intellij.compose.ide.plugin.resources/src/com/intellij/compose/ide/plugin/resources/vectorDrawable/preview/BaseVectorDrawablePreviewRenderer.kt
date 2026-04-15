// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.createDocumentBuilder
import org.w3c.dom.Document
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.JComponent
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

  fun applyOverrides(xmlContent: String, overrideInfo: VectorDrawableOverrideInfo?, errors: StringBuilder): String {
    if (overrideInfo == null) return xmlContent
    val doc = parseXmlDocument(xmlContent, errors) ?: return xmlContent
    return overrideXmlContent(doc, overrideInfo, errors) ?: xmlContent
  }

  abstract fun overrideXmlContent(document: Document, overrideInfo: VectorDrawableOverrideInfo, errors: StringBuilder?): String?

  fun parseXmlDocument(xmlContent: String, errors: StringBuilder?): Document? {
    if (xmlContent.isBlank()) return null
    return try {
      val builder = createDocumentBuilder(namespaceAware = true)
      builder.parse(xmlContent.byteInputStream(Charsets.UTF_8))
    }
    catch (e: Exception) {
      if (Logger.shouldRethrow(e)) throw e
      errors?.append("Exception while parsing XML:\n${e.message}")
      null
    }
  }

  abstract fun convertSvgToVectorDrawable(svgFile: Path, errors: StringBuilder): String?

  abstract fun getVectorDrawableSizeDp(xmlContent: String): Dimension?

  abstract fun doRenderPreview(imageScale: Double, xmlContent: String, errors: StringBuilder): BufferedImage?

  abstract fun adjustIconColor(component: JComponent, image: BufferedImage): BufferedImage

  sealed class RenderResult {
    data class Success(val image: BufferedImage) : RenderResult()
    data class Error(val message: String) : RenderResult()
  }

  data class VectorDrawableOverrideInfo(
    var width: Double = 0.0,
    var height: Double = 0.0,
    var tint: Color? = null,
    var alpha: Double = -1.0,
    var autoMirrored: Boolean = false,
  ) {
    fun needsOverrideWidth(): Boolean = width > 0
    fun needsOverrideHeight(): Boolean = height > 0
    fun needsOverrideAlpha(): Boolean = alpha in 0.0..<1.0
    fun needsOverrideTint(): Boolean = tint != null
    fun tintRgb(): Int = (tint?.rgb ?: 0) and 0xFFFFFF
  }

  companion object {
    private val EP_NAME: ExtensionPointName<BaseVectorDrawablePreviewRenderer> =
      ExtensionPointName.create("com.intellij.compose.ide.plugin.resources.drawablePreviewRenderer")

    fun getInstance(): BaseVectorDrawablePreviewRenderer? =
      EP_NAME.extensionList.firstOrNull()
  }
}