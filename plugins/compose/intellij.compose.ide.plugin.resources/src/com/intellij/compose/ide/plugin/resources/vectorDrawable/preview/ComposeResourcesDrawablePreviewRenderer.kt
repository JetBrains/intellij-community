// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.ComposeResourceDrawableTree
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.createDocumentBuilder
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/** Fallback implementation when Android plugin is not available */
internal class ComposeResourcesDrawablePreviewRenderer : BaseVectorDrawablePreviewRenderer() {
  override fun getVectorDrawableSizeDp(xmlContent: String): Dimension? {
    return try {
      val builder = createDocumentBuilder(namespaceAware = true)
      val doc = builder.parse(xmlContent.byteInputStream(Charsets.UTF_8))
      val root = doc.documentElement

      if (root.tagName != "vector") return null

      val width = parseDoubleDpValue(root.getAttributeNS(ANDROID_URI, "width"))
      val height = parseDoubleDpValue(root.getAttributeNS(ANDROID_URI, "height"))

      if (width == null || height == null) return null
      Dimension(width.roundToInt(), height.roundToInt())
    }
    catch (e: Exception) {
      if (Logger.shouldRethrow(e)) throw e
      null
    }
  }

  override fun doRenderPreview(imageScale: Double, xmlContent: String, errors: StringBuilder): BufferedImage? {
    val tree = getPreviewFromVectorXml(xmlContent, errors) ?: return null
    return renderToImage(imageScale, tree)
  }

  private fun getPreviewFromVectorXml(xmlContent: String, errors: StringBuilder): ComposeResourceDrawableTree? {
    if (xmlContent.isBlank()) return null

    return try {
      val builder = createDocumentBuilder(namespaceAware = true)

      val inputStream = xmlContent.byteInputStream(Charsets.UTF_8)
      val doc = builder.parse(inputStream)

      ComposeResourceDrawableTree().apply { parse(doc) }
    }
    catch (e: Exception) {
      if (Logger.shouldRethrow(e)) throw e
      errors.append(e.message)
      null
    }
  }

  private fun renderToImage(imageScale: Double, composeResourceDrawableTree: ComposeResourceDrawableTree): BufferedImage {
    val width = composeResourceDrawableTree.baseWidth.toDouble()
    val height = composeResourceDrawableTree.baseHeight.toDouble()

    val imageWidth = width * imageScale
    val imageHeight = height * imageScale

    // BufferedImage is used directly to get exact pixel dimensions without HiDPI scaling
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(imageWidth.roundToInt(), imageHeight.roundToInt(), BufferedImage.TYPE_INT_ARGB)
    composeResourceDrawableTree.drawIntoImage(image)
    return image
  }

  private fun parseDoubleDpValue(value: String?): Double? {
    if (value.isNullOrEmpty() || !value.endsWith(DP_SUFFIX)) return null
    return value.dropLast(DP_SUFFIX.length).toDoubleOrNull()
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val DP_SUFFIX = "dp"
  }
}