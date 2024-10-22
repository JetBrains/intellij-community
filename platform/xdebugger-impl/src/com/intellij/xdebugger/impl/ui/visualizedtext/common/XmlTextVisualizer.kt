// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.visualizedtext.TextBasedContentTab
import com.intellij.xdebugger.impl.ui.visualizedtext.TextVisualizerContentType
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedContentTabWithStats
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab
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
    val prettified = tryParseAndPrettify(value)
    if (prettified == null) return emptyList()

    return listOf(object : TextBasedContentTab(), VisualizedContentTabWithStats {
      override val name
        get() = XDebuggerBundle.message("xdebugger.visualized.text.name.xml")
      override val id
        get() = XmlTextVisualizer::class.qualifiedName!!
      override val contentTypeForStats
        get() = TextVisualizerContentType.XML
      override fun formatText() =
        prettified
      override val fileType
        get() = xmlFileType
    })
  }

  override fun detectFileType(value: @NlsSafe String): FileType? =
    if (tryParseAndPrettify(value) != null) xmlFileType else null

  private fun tryParseAndPrettify(value: String): String? =
    try {
      val src = InputSource(StringReader(value))
      val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
      builder.setErrorHandler(null) // suppress printing to stdout errors like "[Fatal Error] :1:1: Content is not allowed in prolog."
      val document = builder.parse(src)

      // Transforming Document to String could also throw exception in case of invalid input (e.g., EA-1461644).
      // So do everything in advance to catch all errors beforehand.
      val transformerFactory = TransformerFactory.newInstance()
      val transformer = transformerFactory.newTransformer()
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")

      val out = StringWriter()
      transformer.transform(DOMSource(document), StreamResult(out))
      out.toString()
    } catch (_: Exception) {
      null
    }

  private val xmlFileType
    get() =
      // Right now we don't want to have an explicit static dependency here.
      // In an ideal world this class would be part of the optional module of the debugger plugin with a dependency on intellij.xml.psi.impl.
      FileTypeManager.getInstance().getStdFileType("XML")

}
