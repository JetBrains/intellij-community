// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.visualizedtext.TextBasedContentTab
import com.intellij.xdebugger.impl.ui.visualizedtext.TextVisualizerContentType
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedContentTabWithStats
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal class XmlTextVisualizer : TextValueVisualizer {
  override fun visualize(value: @NlsSafe String): List<VisualizedContentTab> {
    val xml = tryParse(value)
    if (xml == null) return emptyList()

    return listOf(object : TextBasedContentTab(), VisualizedContentTabWithStats {
      override val name
        get() = XDebuggerBundle.message("xdebugger.visualized.text.name.xml")
      override val id
        get() = XmlTextVisualizer::class.qualifiedName!!
      override val contentTypeForStats
        get() = TextVisualizerContentType.XML
      override fun formatText() =
        prettify(xml)
      override val fileType
        get() =
          // Right now we don't want to have an explicit static dependency here.
          // In an ideal world this class would be part of the optional module of the debugger plugin with a dependency on intellij.xml.psi.impl.
          FileTypeManager.getInstance().getStdFileType("XML") // Right now we don't want to have explicit static dependency hier.
    })
  }

  private fun tryParse(value: String): Document? =
    try {
      val src = InputSource(StringReader(value))
      val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
      builder.setErrorHandler(null) // suppress printing to stdout errors like "[Fatal Error] :1:1: Content is not allowed in prolog."
      builder.parse(src)
    } catch (_: Exception) {
      null
    }

  private fun prettify(document: Document): String {
    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")

    val out = StringWriter()
    transformer.transform(DOMSource(document), StreamResult(out))
    return out.toString()
  }

}
