// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.ui.LafIconLookup
import gnu.trove.THashMap
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.*
import org.w3c.dom.Element
import java.awt.Component
import java.awt.GraphicsConfiguration
import java.awt.Image
import java.io.StringWriter
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Icon
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// jFreeSvg produces not so compact and readable SVG as batik
internal class SvgRenderer(val svgFileDir: Path, private val deviceConfiguration: GraphicsConfiguration) {
  private val xmlTransformer = TransformerFactory.newInstance().newTransformer()

  private val xmlFactory = GenericDOMImplementation.getDOMImplementation().createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null)
  private val context = SVGGeneratorContext.createDefault(xmlFactory)

  init {
    xmlTransformer.setOutputProperty(OutputKeys.METHOD, "xml")
    xmlTransformer.setOutputProperty(OutputKeys.INDENT, "yes")
    xmlTransformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    xmlTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    xmlTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

    context.imageHandler = object : ImageHandlerBase64Encoder() {
      override fun handleImage(image: Image, imageElement: Element, generatorContext: SVGGeneratorContext) {
        imageElement.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", findImagePath(image))
      }

      private fun findImagePath(image: Image): String {
        fun isImage(iconWrapper: Icon): Boolean {
          if (iconWrapper === AllIcons.Actions.Stub) {
            return false
          }

          val thatImage = (iconWrapper as IconLoader.CachedImageIcon).doGetRealIcon()?.image
          return thatImage === image
        }

        for (name in arrayOf("checkBox", "radio", "gear", "spinnerRight")) {
          val iconWrapper = when (name) {
            "gear" -> IconLoader.getIcon("/general/gear.png")
            else -> LafIconLookup.findIcon(name)
          } ?: continue

          if (isImage(iconWrapper)) {
            return getIconRelativePath(iconWrapper.toString())
          }
        }
        for (name in arrayOf("checkBox", "radio")) {
          val iconWrapper = LafIconLookup.findIcon(name, selected = true) ?: continue
          if (isImage(iconWrapper)) {
            return getIconRelativePath(iconWrapper.toString())
          }
        }

        throw RuntimeException("unknown image")
      }
    }

    context.errorHandler = object: ErrorHandler {
      override fun handleError(error: SVGGraphics2DIOException) = throw error

      override fun handleError(error: SVGGraphics2DRuntimeException) = throw error
    }

    class PrefixInfo {
      var currentId = 0
    }

    context.idGenerator = object : SVGIDGenerator() {
      private val prefixMap = THashMap<String, PrefixInfo>()

      override fun generateID(prefix: String): String {
        val info = prefixMap.getOrPut(prefix) { PrefixInfo() }
        return "${if (prefix == "clipPath") "" else prefix}${info.currentId++}"
      }
    }
  }

  private fun getIconRelativePath(outputPath: String): String {
    for ((moduleName, relativePath) in mapOf("intellij.platform.icons" to "platform/icons/src",
                                             "intellij.platform.ide.impl" to "platform/platform-impl/src")) {
      val index = outputPath.indexOf(moduleName)
      if (index > 0) {
        val iconPath = Paths.get(PathManagerEx.getCommunityHomePath(), relativePath, outputPath.substring(index + moduleName.length + 1 /* slash */))
        assertThat(iconPath).exists()
        return FileUtilRt.toSystemIndependentName(svgFileDir.relativize(iconPath).toString())
      }
    }

    throw RuntimeException("unknown icon location ($outputPath)")
  }

  // CSS (style) not used - attributes more readable and shorter
  // separate styles (in the defs) also not suitable, so, we keep it simple as is
  private fun svgGraphicsToString(svgGenerator: SVGGraphics2D, component: Component): String {
    val writer = StringWriter()
    writer.use {
      val root = svgGenerator.root
      root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", SVGSyntax.SVG_NAMESPACE_URI)
      root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xlink", "http://www.w3.org/1999/xlink")

      root.setAttributeNS(null, "viewBox", "0 0 ${component.width} ${component.height}")

      xmlTransformer.transform(DOMSource(root), StreamResult(writer))
    }
    // xlink is not used in some files and optimize imports on commit can modify file, so, as simple solution, disable inspection
    val result = "<!--suppress XmlUnusedNamespaceDeclaration -->\n" + writer
      .toString()
      // &#27;Remember
      // no idea why transformer/batik doesn't escape it correctly
      .replace(">&#27;", ">&amp;")
    return if (SystemInfoRt.isWindows) StringUtilRt.convertLineSeparators(result) else result
  }

  fun render(component: Component): String {
    val svgGenerator = SvgGraphics2dWithDeviceConfiguration(context, deviceConfiguration)
    component.paint(svgGenerator)
    return svgGraphicsToString(svgGenerator, component)
  }
}

private class SvgGraphics2dWithDeviceConfiguration : SVGGraphics2D {
  private val _deviceConfiguration: GraphicsConfiguration

  constructor(context: SVGGeneratorContext, _deviceConfiguration: GraphicsConfiguration) : super(context, false) {
    this._deviceConfiguration = _deviceConfiguration
  }

  private constructor(g: SvgGraphics2dWithDeviceConfiguration): super(g) {
    this._deviceConfiguration = g._deviceConfiguration
  }

  override fun getDeviceConfiguration() = _deviceConfiguration

  override fun create() = SvgGraphics2dWithDeviceConfiguration(this)
}