// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.ui.IconCache
import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.ImageHandlerBase64Encoder
import org.apache.batik.svggen.SVGGeneratorContext
import org.apache.batik.svggen.SVGGraphics2D
import org.apache.batik.svggen.SVGSyntax
import org.apache.xmlgraphics.java2d.GraphicsConfigurationWithTransparency
import org.w3c.dom.Element
import java.awt.Component
import java.awt.Image
import java.io.StringWriter
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// jFreeSvg produces not so compact and readable SVG as batik
internal class SvgRenderer(private val svgFileDir: Path) {
  private val xmlTransformer = TransformerFactory.newInstance().newTransformer()

  // todo check on Retina - does it works or not (is Retina disabled or not)
  private val deviceConfiguration = GraphicsConfigurationWithTransparency()

  private val xmlFactory = GenericDOMImplementation.getDOMImplementation().createDocument("http://www.w3.org/2000/svg", "svg", null)
  private val context = SVGGeneratorContext.createDefault(xmlFactory)

  init {
    xmlTransformer.setOutputProperty(OutputKeys.METHOD, "xml")
    xmlTransformer.setOutputProperty(OutputKeys.INDENT, "yes")
    xmlTransformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    xmlTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    xmlTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

    context.imageHandler = object : ImageHandlerBase64Encoder() {
      override fun handleImage(image: Image, imageElement: Element, generatorContext: SVGGeneratorContext) {
        val url = findImagePath(image)
        if (url == null) {
          throw RuntimeException("unknown image")
        }

        imageElement.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", url)
      }

      private fun findImagePath(image: Image): String? {
        for (name in arrayOf("checkBox", "radio", "gear", "spinnerRight")) {
          val iconWrapper = when (name) {
                              "gear" -> IconLoader.getIcon("/general/gear.png")
                              else -> IconCache.getIcon(name)
                            } as? IconLoader.CachedImageIcon ?: continue
          if (iconWrapper.doGetRealIcon()?.image == image) {
            return getIconRelativePath(iconWrapper)
          }
        }
        for (name in arrayOf("checkBox", "radio")) {
          val iconWrapper = IconCache.getIcon(name, true, false) as IconLoader.CachedImageIcon
          if (iconWrapper.doGetRealIcon()?.image == image) {
            return getIconRelativePath(iconWrapper)
          }
        }
        return null
      }
    }
  }

  private fun getIconRelativePath(iconWrapper: IconLoader.CachedImageIcon): String {
    val outputPath = iconWrapper.toString()
    for ((moduleName, relativePath) in mapOf("intellij.platform.icons" to "platform/icons",
                                             "intellij.platform.ide.impl" to "platform/platform-impl/src")) {
      val index = outputPath.indexOf(moduleName)
      if (index > 0) {
        return FileUtilRt.toSystemIndependentName(svgFileDir
          .relativize(Paths.get(PathManagerEx.getCommunityHomePath(), relativePath, outputPath.substring(index + moduleName.length + 1 /* slash */)))
          .toString())
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

      val bounds = component.bounds
      root.setAttributeNS(null, "viewBox", "${bounds.x} ${bounds.y} ${bounds.width} ${bounds.height}")

      xmlTransformer.transform(DOMSource(root), StreamResult(writer))
    }
    return writer
      .toString()
      // &#27;Remember
      // no idea why transformer/batik doesn't escape it correctly
      .replace(">&#27;", ">&amp;")
  }

  fun render(component: Component): String {
    val svgGenerator = object : SVGGraphics2D(context, false) {
      override fun getDeviceConfiguration() = this@SvgRenderer.deviceConfiguration
    }
    component.paint(svgGenerator)
    return svgGraphicsToString(svgGenerator, component)
  }
}