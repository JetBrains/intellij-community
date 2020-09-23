// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg

import org.apache.batik.anim.dom.SVG12DOMImplementation
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.anim.dom.SVGOMDocument
import org.apache.batik.constants.XMLConstants
import org.apache.batik.dom.AbstractDocument
import org.apache.batik.dom.util.DocumentDescriptor
import org.apache.batik.dom.util.HashTableStack
import org.apache.batik.dom.util.XMLSupport
import org.apache.batik.transcoder.TranscoderException
import org.apache.batik.util.ParsedURL
import org.w3c.dom.*
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InterruptedIOException
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

class SaxSvgDocumentFactory : DefaultHandler(), LexicalHandler {
  companion object {
    private var saxFactory = SAXParserFactory.newInstance()

    init {
      try {
        saxFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        saxFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        saxFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      }
      catch (e: SAXNotRecognizedException) {
        e.printStackTrace()
      }
      catch (e: SAXNotSupportedException) {
        e.printStackTrace()
      }
      catch (e: ParserConfigurationException) {
        e.printStackTrace()
      }
    }
  }

  /**
   * The DOM implementation used to create the document.
   */
  private var implementation: DOMImplementation = SVGDOMImplementation.getDOMImplementation()

  /**
   * The SAX2 parser object.
   */
  private var parser: XMLReader? = null

  /**
   * The created document.
   */
  private var document: Document? = null

  /**
   * The created document descriptor.
   */
  private var documentDescriptor: DocumentDescriptor? = null

  /**
   * Whether a document descriptor must be generated.
   */
  private var createDocumentDescriptor: Boolean = false

  /**
   * The current node.
   */
  private var currentNode: Node? = null

  /**
   * The locator.
   */
  private var locator: Locator? = null

  /**
   * Contains collected string data.  May be Text, CDATA or Comment.
   */
  private val stringBuilder = StringBuilder()

  /**
   * The DTD to use when the document is created.
   */
  private var doctype: DocumentType? = null

  /**
   * Indicates if stringBuilder has content, needed in case of
   * zero sized "text" content.
   */
  private var stringContent: Boolean = false

  /**
   * True if the parser is currently parsing a DTD.
   */
  private var inDTD: Boolean = false

  /**
   * True if the parser is currently parsing a CDATA section.
   */
  private var inCDATA: Boolean = false

  /**
   * Whether the parser still hasn't read the document element's
   * opening tag.
   */
  private var inProlog: Boolean = false

  /**
   * Whether the document just parsed was standalone.
   */
  private var isStandalone: Boolean = false

  /**
   * XML version of the document just parsed.
   */
  private var xmlVersion: String? = null

  /**
   * The stack used to store the namespace URIs.
   */
  private var namespaces = HashTableStack()

  override fun setDocumentLocator(l: Locator?) {
    locator = l
  }

  override fun fatalError(e: SAXParseException) = throw e

  override fun error(e: SAXParseException) = throw e

  override fun warning(e: SAXParseException?) {
  }

  override fun startDocument() {
    namespaces = HashTableStack()
    val namespaces = namespaces
    namespaces.put("xml", XMLSupport.XML_NAMESPACE_URI)
    namespaces.put("xmlns", XMLSupport.XMLNS_NAMESPACE_URI)
    namespaces.put("", null)

    inDTD = false
    inCDATA = false
    inProlog = true
    currentNode = null
    document = null
    doctype = null
    isStandalone = false
    xmlVersion = XMLConstants.XML_VERSION_10

    stringBuilder.setLength(0)
    stringContent = false

    if (createDocumentDescriptor) {
      documentDescriptor = DocumentDescriptor()
    }
    else {
      documentDescriptor = null
    }
  }

  override fun startElement(uri: String?, localName: String?, rawName: String?, attributes: Attributes) {
    if (inProlog) {
      inProlog = false
      if (parser != null) {
        try {
          isStandalone = parser!!.getFeature("http://xml.org/sax/features/is-standalone")
        }
        catch (ignore: SAXNotRecognizedException) {
        }

        try {
          xmlVersion = parser!!.getProperty("http://xml.org/sax/properties/document-xml-version") as String
        }
        catch (ignore: SAXNotRecognizedException) {
        }
      }
    }

    // namespaces resolution
    namespaces.push()
    var version: String? = null
    for (i in 0 until attributes.length) {
      val qName = attributes.getQName(i)
      val sLen = qName.length
      if (sLen < 5) {
        continue
      }
      if (qName == "version") {
        version = attributes.getValue(i)
        continue
      }
      if (!qName.startsWith("xmlns")) {
        continue
      }

      if (sLen == 5) {
        namespaces.put("", attributes.getValue(i)?.takeIf(String::isNotEmpty))
      }
      else if (qName[5] == ':') {
        namespaces.put(qName.substring(6), attributes.getValue(i)?.takeIf(String::isNotEmpty))
      }
    }

    // Add any collected String Data before element.
    appendStringData()

    // Element creation
    var idx = rawName!!.indexOf(':')
    val nsp = when (idx) {
      -1, rawName.length - 1 -> ""
      else -> rawName.substring(0, idx)
    }

    val e: Element
    var nsURI: String? = namespaces.get(nsp)
    if (currentNode == null) {
      implementation = getDOMImplementation(version)
      document = implementation.createDocument(nsURI, rawName, doctype)
      e = document!!.documentElement
      currentNode = e
    }
    else {
      e = document!!.createElementNS(nsURI, rawName)
      currentNode!!.appendChild(e)
      currentNode = e
    }

    // Storage of the line number.
    if (createDocumentDescriptor && locator != null) {
      documentDescriptor!!.setLocation(e, locator!!.lineNumber, locator!!.columnNumber)
    }

    // Attributes creation
    for (i in 0 until attributes.length) {
      val qName = attributes.getQName(i)
      if (qName == "xmlns") {
        e.setAttributeNS(XMLSupport.XMLNS_NAMESPACE_URI, qName, attributes.getValue(i))
      }
      else {
        idx = qName.indexOf(':')
        nsURI = when (idx) {
          -1 -> null
          else -> namespaces.get(qName.substring(0, idx))
        }
        e.setAttributeNS(nsURI, qName, attributes.getValue(i))
      }
    }
  }

  override fun endElement(uri: String?, localName: String?, rawName: String?) {
    appendStringData() // add string data if any.

    if (currentNode != null) {
      currentNode = currentNode!!.parentNode
    }
    namespaces.pop()
  }

  private fun appendStringData() {
    if (!stringContent) {
      return
    }

    val str = stringBuilder.toString()
    stringBuilder.setLength(0) // reuse buffer.
    stringContent = false
    if (currentNode != null) {
      val n: Node
      when {
        inCDATA -> n = document!!.createCDATASection(str)
        else -> n = document!!.createTextNode(str)
      }
      currentNode!!.appendChild(n)
    }
  }

  override fun characters(ch: CharArray?, start: Int, length: Int) {
    stringBuilder.append(ch, start, length)
    stringContent = true
  }

  override fun ignorableWhitespace(ch: CharArray?, start: Int, length: Int) {
    stringBuilder.append(ch, start, length)
    stringContent = true
  }

  override fun processingInstruction(target: String?, data: String?) {
  }

  override fun startDTD(name: String, publicId: String, systemId: String) {
    appendStringData() // Add collected string data before entering DTD
    doctype = implementation.createDocumentType(name, publicId, systemId)
    inDTD = true
  }

  override fun endDTD() {
    inDTD = false
  }

  override fun startEntity(name: String) {
  }

  override fun endEntity(name: String) {
  }

  override fun startCDATA() {
    appendStringData() // Add any collected String Data before CData
    inCDATA = true
    stringContent = true // always create CDATA even if empty.
  }

  override fun endCDATA() {
    appendStringData() // Add the CDATA section
    inCDATA = false
  }

  override fun comment(ch: CharArray, start: Int, length: Int) {
  }

  fun createDocument(uri: String?, inputSource: InputSource): Document {
    try {
      val parser = saxFactory.newSAXParser().xmlReader!!
      this.parser = parser

      parser.contentHandler = this
      parser.dtdHandler = this
      parser.entityResolver = this
      parser.errorHandler = this

      parser.setFeature("http://xml.org/sax/features/namespaces", true)
      parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true)
      parser.setFeature("http://xml.org/sax/features/validation", false)
      parser.setFeature("http://xml.org/sax/features/external-general-entities", false)
      parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      parser.setProperty("http://xml.org/sax/properties/lexical-handler", this)

      parser.parse(inputSource)
    }
    catch (e: SAXException) {
      val ex = e.exception
      if (ex != null && ex is InterruptedIOException) {
        throw ex
      }
      throw TranscoderException(e)
    }

    currentNode = null
    val ret = document
    document = null
    doctype = null
    locator = null
    parser = null
    val result = ret!!
    val docElem = result.documentElement
    val docElemNS = docElem.namespaceURI
    @Suppress("SuspiciousEqualsCombination")
    if (docElemNS !== SVGDOMImplementation.SVG_NAMESPACE_URI && (docElemNS == null || docElemNS != SVGDOMImplementation.SVG_NAMESPACE_URI)) {
      throw IOException(
        "Root element namespace does not match that requested:\nRequested: ${SVGDOMImplementation.SVG_NAMESPACE_URI}\nFound: $docElemNS")
    }
    if (docElem.localName != "svg") {
      throw IOException("Root element does not match that requested:\nRequested: svg\nFound: ${docElem.localName}")
    }
    if (uri != null) {
      (result as SVGOMDocument).parsedURL = ParsedURL(uri)
    }

    val d = result as AbstractDocument
    d.documentURI = uri
    d.xmlStandalone = isStandalone
    d.xmlVersion = xmlVersion
    return result
  }
}

private fun getDOMImplementation(ver: String?): DOMImplementation {
  if (ver == null || ver.isEmpty() || ver == "1.0" || ver == "1.1") {
    return SVGDOMImplementation.getDOMImplementation()
  }
  else if (ver == "1.2") {
    return SVG12DOMImplementation.getDOMImplementation()
  }
  else {
    throw RuntimeException("Unsupported SVG version '$ver'")
  }
}

private interface PreInfo {
  fun createNode(doc: Document): Node
}

private class ProcessingInstructionInfo(var target: String?, var data: String?) : PreInfo {
  override fun createNode(doc: Document): Node = doc.createProcessingInstruction(target, data)
}

private class CommentInfo(var comment: String) : PreInfo {
  override fun createNode(doc: Document): Node = doc.createComment(comment)
}

private class CDataInfo(var cdata: String) : PreInfo {
  override fun createNode(doc: Document): Node = doc.createCDATASection(cdata)
}

private class TextInfo(var text: String) : PreInfo {
  override fun createNode(doc: Document): Node = doc.createTextNode(text)
}