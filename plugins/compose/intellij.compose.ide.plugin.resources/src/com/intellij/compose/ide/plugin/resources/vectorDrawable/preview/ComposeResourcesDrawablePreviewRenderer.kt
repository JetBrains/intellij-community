// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.ComposeResourceDrawableTree
import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgConverter
import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgTree.Companion.formatFloatValue
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.util.createDocumentBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.awt.image.ByteLookupTable
import java.awt.image.LookupOp
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.swing.JComponent
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.roundToInt

/** Fallback implementation when the Android plugin is not available */
internal class ComposeResourcesDrawablePreviewRenderer : BaseVectorDrawablePreviewRenderer() {

  private val COLOR_INVERSION_TABLE = ByteArray(256) { (3 * (255 - it) / 4).toByte() }

  override fun convertSvgToVectorDrawable(svgFile: Path, errors: StringBuilder): String? {
    return try {
      val outputStream = ByteArrayOutputStream()
      val svgTree = ComposeResourcesSvgConverter().parse(svgFile)
      if (svgTree.hasLeafNode) svgTree.writeXml(outputStream)

      val errorMessage = svgTree.getErrorMessage()
      errors.append(errorMessage)

      outputStream.toString(StandardCharsets.UTF_8)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      errors.append(e.message)
      null
    }
  }

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
      rethrowControlFlowException(e)
      null
    }
  }

  override fun doRenderPreview(imageScale: Double, xmlContent: String, errors: StringBuilder): BufferedImage? {
    return try {
      val doc = parseXmlDocument(xmlContent, errors) ?: return null
      val tree = ComposeResourceDrawableTree().apply { parse(doc) }

      renderToImage(imageScale, tree)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
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

  // Based on VdIcon.adjustIconColor, if the background is dark, invert the black pixels to white
  override fun adjustIconColor(component: JComponent, image: BufferedImage): BufferedImage {
    val background = component.background
    if (background != null && background.red < 128) {
      val table = ByteLookupTable(0, COLOR_INVERSION_TABLE)
      val invertFilter = LookupOp(table, null)
      return invertFilter.filter(image, null)
    }
    return image
  }

  // Based on VdPreview.overrideXmlContent
  override fun overrideXmlContent(
    document: Document,
    overrideInfo: VectorDrawableOverrideInfo,
    errors: StringBuilder?,
  ): String? {
    var contentChanged = false
    val root = document.documentElement

    if (overrideInfo.needsOverrideWidth() && setDimension(root, "android:width", overrideInfo.width)) {
      contentChanged = true
    }

    if (overrideInfo.needsOverrideHeight() && setDimension(root, "android:height", overrideInfo.height)) {
      contentChanged = true
    }

    if (overrideInfo.needsOverrideAlpha()) {
      val value = formatFloatValue(overrideInfo.alpha)
      if (setAttributeValue(root, "android:alpha", value)) contentChanged = true
    }

    if (overrideInfo.needsOverrideTint()) {
      val value = String.format("#%06X", overrideInfo.tintRgb())
      if (setAttributeValue(root, "android:tint", value)) contentChanged = true
    }

    if (overrideInfo.autoMirrored && setAttributeValue(root, "android:autoMirrored", "true")) {
      contentChanged = true
    }

    if (!contentChanged) return null

    val stringOut = StringWriter()
    try {
      val transformer = getPrettyPrintTransformer()
      transformer.transform(DOMSource(document), StreamResult(stringOut))
    }
    catch (e: TransformerException) {
      errors?.append("Exception while serializing XML file:\n")?.append(e.message)
    }

    return stringOut.toString()
  }

  private fun getPrettyPrintTransformer(): Transformer {
    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    transformer.setOutputProperty(OutputKeys.METHOD, "xml")
    transformer.setOutputProperty(OutputKeys.VERSION, "1.0")
    return transformer
  }

  private fun setAttributeValue(element: Element, attrName: String, value: String): Boolean {
    val oldValue = element.getAttribute(attrName)
    element.setAttribute(attrName, value)
    return value != oldValue
  }

  private fun setDimension(element: Element, attrName: String, value: Double): Boolean {
    val newValue = formatFloatValue(value) + "dp"
    return setAttributeValue(element, attrName, newValue)
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val DP_SUFFIX = "dp"
  }
}