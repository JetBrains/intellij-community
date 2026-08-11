// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter

import com.intellij.util.createDocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.SAXParserFactory

/** XML parser that tracks line numbers for each node */
internal object ComposeResourcesXmlParser {
  private const val LINE_NUMBER_KEY = "lineNumber"

  private val domBuilderFactory = createDocumentBuilderFactory(namespaceAware = true)

  @Suppress("HttpUrlsUsage")
  private val saxParserFactory = SAXParserFactory.newInstance().apply {
    isNamespaceAware = true
    setFeature("http://xml.org/sax/features/external-general-entities", false)
    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
  }

  fun parse(file: Path, parseErrors: MutableList<String?>): Document? = try {
    Files.newInputStream(file).buffered().use { parse(it, parseErrors) }
  }
  catch (e: Exception) {
    parseErrors.add(e.message ?: "Failed to read file")
    null
  }

  fun parse(inputStream: InputStream, parseErrors: MutableList<String?>): Document {
    val contentString = inputStream.readBytes().decodeToString()
    val lines = contentString.lines()

    val docBuilder = synchronized(domBuilderFactory) { domBuilderFactory.newDocumentBuilder() }
    val document = docBuilder.newDocument()
    val handler = DomBuilder(document, lines)

    try {
      val saxParser = synchronized(saxParserFactory) { saxParserFactory.newSAXParser() }
      saxParser.parse(InputSource(StringReader(contentString)), handler)
    }
    catch (e: Exception) {
      parseErrors.add(e.message ?: "Parse error")
      handler.closeUnfinishedElements()
    }

    return document
  }

  fun getStartLine(node: Node): Int = (node.getUserData(LINE_NUMBER_KEY) as? Int) ?: 1

  private class DomBuilder(val document: Document, private val lines: List<String>) : DefaultHandler() {
    private var locator: Locator? = null
    private val elementStack = mutableListOf<Element>()
    private val textBuffer = StringBuilder()

    override fun setDocumentLocator(locator: Locator) {
      this.locator = locator
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
      flushText()

      val element = if (!uri.isNullOrEmpty()) document.createElementNS(uri, qName)
      else document.createElement(qName ?: localName)

      attributes?.let { attrs ->
        for (i in 0 until attrs.length) {
          val attrUri = attrs.getURI(i)
          if (!attrUri.isNullOrEmpty()) element.setAttributeNS(attrUri, attrs.getQName(i), attrs.getValue(i))
          else element.setAttribute(attrs.getQName(i) ?: attrs.getLocalName(i), attrs.getValue(i))
        }
      }

      locator?.let { loc ->
        val startLine = findElementStartLine(localName ?: qName ?: "", loc.lineNumber)
        element.setUserData(LINE_NUMBER_KEY, startLine, null)
      }
      elementStack.add(element)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
      flushText()
      if (elementStack.isNotEmpty()) {
        val element = elementStack.removeLast()
        (elementStack.lastOrNull() ?: document).appendChild(element)
      }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
      textBuffer.appendRange(ch, start, start + length)
    }

    fun closeUnfinishedElements() {
      flushText()
      while (elementStack.isNotEmpty()) {
        val element = elementStack.removeLast()
        (elementStack.lastOrNull() ?: document).appendChild(element)
      }
    }

    private fun flushText() {
      if (textBuffer.isEmpty()) return
      val text = document.createTextNode(textBuffer.toString())
      (elementStack.lastOrNull() ?: document.documentElement)?.appendChild(text)
      textBuffer.clear()
    }

    private fun findElementStartLine(elementName: String, endLine: Int): Int {
      val searchPattern = "<$elementName"
      for (lineNum in endLine downTo 1) {
        if (lines.getOrNull(lineNum - 1)?.contains(searchPattern) == true) return lineNum
      }
      return endLine
    }
  }
}