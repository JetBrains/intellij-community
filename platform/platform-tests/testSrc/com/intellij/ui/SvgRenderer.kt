// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGeneratorContext
import org.apache.batik.svggen.SVGGraphics2D
import org.apache.batik.svggen.SVGSyntax
import org.apache.xmlgraphics.java2d.GraphicsConfigurationWithTransparency
import java.awt.Component
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// jFreeSvg produces not so compact and readable SVG as batik
internal class SvgRenderer {
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
      .replace(">&#27;", ">&amp")
  }

  fun render(component: Component): String {
    val svgGenerator = object : SVGGraphics2D(context, false) {
      override fun getDeviceConfiguration() = this@SvgRenderer.deviceConfiguration
    }
    component.paint(svgGenerator)
    return svgGraphicsToString(svgGenerator, component)
  }
}