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
import javax.xml.parsers.DocumentBuilder
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

  private fun tryParseAndPrettify(value: String): String? {
    if (value.firstOrNull { !it.isWhitespace() } != '<') {
      // Fast-path for the trivial non-xml case.
      // It takes much time to report an error about invalid XML, try to prevent it.
      return null
    }

    return try {
      val src = InputSource(StringReader(value))
      val builder = createDocumentBuilder()
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
    }
    catch (_: Exception) {
      null
    }
  }

  private val xmlFileType
    get() =
      // Right now we don't want to have an explicit static dependency here.
      // In an ideal world this class would be part of the optional module of the debugger plugin with a dependency on intellij.xml.psi.impl.
      FileTypeManager.getInstance().getStdFileType("XML")

  @Suppress("HttpUrlsUsage")
  fun createDocumentBuilder(): DocumentBuilder {
    // from https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
    val dbf = DocumentBuilderFactory.newDefaultInstance()
    return try {
      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
      // JDK7+ - http://xml.org/sax/features/external-general-entities
      //This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
      var feature = "http://xml.org/sax/features/external-general-entities"
      dbf.setFeature(feature, false)

      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
      // JDK7+ - http://xml.org/sax/features/external-parameter-entities
      //This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
      feature = "http://xml.org/sax/features/external-parameter-entities"
      dbf.setFeature(feature, false)

      // Disable external DTDs as well
      feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
      dbf.setFeature(feature, false)

      // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
      dbf.isXIncludeAware = false
      dbf.isExpandEntityReferences = false

      // And, per Timothy Morgan: "If for some reason support for inline DOCTYPE is a requirement, then
      // ensure the entity settings are disabled (as shown above) and beware that SSRF attacks
      // (http://cwe.mitre.org/data/definitions/918.html) and denial
      // of service attacks (such as a billion laughs or decompression bombs via "jar:") are a risk."
      dbf.newDocumentBuilder()
    }
    catch (throwable: Throwable) {
      throw IllegalStateException("Unable to create DOM parser", throwable)
    }
  }
}
