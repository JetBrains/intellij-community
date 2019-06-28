// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg

import org.apache.batik.anim.dom.SAXSVGDocumentFactory
import org.apache.batik.anim.dom.SVG12DOMImplementation
import org.apache.batik.anim.dom.SVGDOMImplementation
import org.apache.batik.anim.dom.SVGOMDocument
import org.apache.batik.constants.XMLConstants
import org.apache.batik.dom.AbstractDocument
import org.apache.batik.dom.util.DocumentDescriptor
import org.apache.batik.dom.util.HashTableStack
import org.apache.batik.dom.util.SAXIOException
import org.apache.batik.dom.util.XMLSupport
import org.apache.batik.util.HaltingThread
import org.apache.batik.util.ParsedURL
import org.w3c.dom.*
import org.xml.sax.*
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.StringReader
import java.util.*
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

class SaxSvgDocumentFactory : SaxDocumentFactory(SVGDOMImplementation.getDOMImplementation()) {
  companion object {
    private val LOCK = Any()

    /**
     * Key used for public identifiers
     */
    private const val KEY_PUBLIC_IDS = "publicIds"

    /**
     * Key used for public identifiers
     */
    private const val KEY_SKIPPABLE_PUBLIC_IDS = "skippablePublicIds"

    /**
     * Key used for the skippable DTD substitution
     */
    private const val KEY_SKIP_DTD = "skipDTD"

    /**
     * Key used for system identifiers
     */
    private const val KEY_SYSTEM_ID = "systemId."

    /**
     * The accepted DTD public IDs.
     */
    private var dtdIds: String? = null

    /**
     * The DTD public IDs we know we can skip.
     */
    private var skippableDtdIds: String? = null

    /**
     * The DTD content to use when skipping
     */
    private var skip_dtd: String? = null

    private var dtdProps: Properties? = null
  }

  override fun createDocument(uri: String?, inputStream: InputStream): Document {
    val inputSource = InputSource(inputStream)
    inputSource.systemId = uri
    val doc = super.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", inputSource)
    if (uri != null) {
      (doc as SVGOMDocument).parsedURL = ParsedURL(uri)
    }

    val d = doc as AbstractDocument
    d.documentURI = uri
    d.xmlStandalone = isStandalone
    d.xmlVersion = xmlVersion
    return doc
  }

  override fun getDOMImplementation(ver: String?): DOMImplementation {
    if (ver == null || ver.isEmpty() || ver == "1.0" || ver == "1.1") {
      return SVGDOMImplementation.getDOMImplementation()
    }
    else if (ver == "1.2") {
      return SVG12DOMImplementation.getDOMImplementation()
    }
    throw RuntimeException("Unsupported SVG version '$ver'")
  }

  override fun resolveEntity(publicId: String?, systemId: String?): InputSource? {
    synchronized(LOCK) {
      if (dtdProps == null) {
        dtdProps = Properties()
        try {
          val cls = SAXSVGDocumentFactory::class.java
          @Suppress("SpellCheckingInspection")
          val `is` = cls.getResourceAsStream("resources/dtdids.properties")
          dtdProps!!.load(`is`)
        }
        catch (ioe: IOException) {
          throw SAXException(ioe)
        }
      }

      if (dtdIds == null) {
        dtdIds = dtdProps!!.getProperty(KEY_PUBLIC_IDS)
      }

      if (skippableDtdIds == null) {
        skippableDtdIds = dtdProps!!.getProperty(KEY_SKIPPABLE_PUBLIC_IDS)
      }

      if (skip_dtd == null) {
        skip_dtd = dtdProps!!.getProperty(KEY_SKIP_DTD)
      }
    }

    if (publicId == null) {
      return null // Let SAX Parser find it.
    }

    if (skippableDtdIds!!.indexOf(publicId) != -1) {
      // We are not validating and this is a DTD we can
      // safely skip so do it...  Here we provide just enough
      // of the DTD to keep stuff running (set svg and
      // xlink namespaces).
      return InputSource(StringReader(skip_dtd!!))
    }

    if (dtdIds!!.indexOf(publicId) != -1) {
      val localSystemId = dtdProps!!.getProperty(KEY_SYSTEM_ID + publicId.replace(' ', '_'))

      if (localSystemId != null && "" != localSystemId) {
        return InputSource(javaClass.getResource(localSystemId).toString())
      }
    }

    // Let the SAX parser find the entity.
    return null
  }
}

open class SaxDocumentFactory(impl: DOMImplementation) : DefaultHandler(), LexicalHandler {
  companion object {
    internal var saxFactory = SAXParserFactory.newInstance()

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
  protected var implementation: DOMImplementation = impl

  /**
   * The SAX2 parser object.
   */
  protected var parser: XMLReader? = null

  /**
   * The created document.
   */
  protected var document: Document? = null

  /**
   * The created document descriptor.
   */
  protected var documentDescriptor: DocumentDescriptor? = null

  /**
   * Whether a document descriptor must be generated.
   */
  private var createDocumentDescriptor: Boolean = false

  /**
   * The current node.
   */
  protected var currentNode: Node? = null

  /**
   * The locator.
   */
  protected var locator: Locator? = null

  /**
   * Contains collected string data.  May be Text, CDATA or Comment.
   */
  protected var stringBuffer = StringBuffer()

  /**
   * The DTD to use when the document is created.
   */
  protected var doctype: DocumentType? = null

  /**
   * Indicates if stringBuffer has content, needed in case of
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
  protected var isStandalone: Boolean = false

  /**
   * XML version of the document just parsed.
   */
  protected var xmlVersion: String? = null

  /**
   * The stack used to store the namespace URIs.
   */
  protected var namespaces: HashTableStack? = null

  /**
   * The error handler.
   */
  protected var errorHandler: ErrorHandler? = null

  /**
   * Various elements encountered prior to real document root element.
   * List of PreInfo objects.
   */
  private var preInfo: MutableList<Any>? = null

  protected interface PreInfo {
    fun createNode(doc: Document): Node
  }

  internal class ProcessingInstructionInfo(var target: String?, var data: String?) : PreInfo {
    override fun createNode(doc: Document): Node = doc.createProcessingInstruction(target, data)
  }

  internal class CommentInfo(var comment: String) : PreInfo {
    override fun createNode(doc: Document): Node = doc.createComment(comment)
  }

  internal class CDataInfo(var cdata: String) : PreInfo {
    override fun createNode(doc: Document): Node = doc.createCDATASection(cdata)
  }

  internal class TextInfo(var text: String) : PreInfo {
    override fun createNode(doc: Document): Node = doc.createTextNode(text)
  }

  open fun createDocument(uri: String?, inputStream: InputStream): Document {
    val inp = InputSource(inputStream)
    inp.systemId = uri
    return createDocument(inp)
  }

  protected fun createDocument(ns: String?, root: String, `is`: InputSource): Document {
    val ret = createDocument(`is`)
    val docElem = ret.documentElement

    var lName = root
    var nsURI = ns
    if (ns == null) {
      val idx = lName.indexOf(':')
      val nsp = when (idx) {
        -1, lName.length - 1 -> ""
        else -> lName.substring(0, idx)
      }
      nsURI = namespaces!!.get(nsp)
      if (idx != -1 && idx != lName.length - 1) {
        lName = lName.substring(idx + 1)
      }
    }


    val docElemNS = docElem.namespaceURI
    @Suppress("SuspiciousEqualsCombination")
    if (docElemNS !== nsURI && (docElemNS == null || docElemNS != nsURI))
      throw IOException("Root element namespace does not match that requested:\n" +
                        "Requested: " + nsURI + "\n" +
                        "Found: " + docElemNS)

    if (docElemNS != null) {
      if (docElem.localName != lName)
        throw IOException("Root element does not match that requested:\n" +
                          "Requested: " + lName + "\n" +
                          "Found: " + docElem.localName)
    }
    else {
      if (docElem.nodeName != lName)
        throw IOException("Root element does not match that requested:\n" +
                          "Requested: " + lName + "\n" +
                          "Found: " + docElem.nodeName)
    }

    return ret
  }

  protected fun createDocument(`is`: InputSource): Document {
    try {
      val saxParser = try {
        saxFactory.newSAXParser()
      }
      catch (pce: ParserConfigurationException) {
        throw IOException("Could not create SAXParser: " + pce.message)
      }

      parser = saxParser.xmlReader

      parser!!.contentHandler = this
      parser!!.dtdHandler = this
      parser!!.entityResolver = this
      parser!!.errorHandler = if (errorHandler == null)
        this
      else
        errorHandler

      parser!!.setFeature("http://xml.org/sax/features/namespaces", true)
      parser!!.setFeature("http://xml.org/sax/features/namespace-prefixes", true)
      parser!!.setFeature("http://xml.org/sax/features/validation", false)
      parser!!.setFeature("http://xml.org/sax/features/external-general-entities", false)
      parser!!.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      parser!!.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      parser!!.setProperty("http://xml.org/sax/properties/lexical-handler", this)
      parser!!.parse(`is`)
    }
    catch (e: SAXException) {
      val ex = e.exception
      if (ex != null && ex is InterruptedIOException) {
        throw ex
      }
      throw SAXIOException(e)
    }

    currentNode = null
    val ret = document
    document = null
    doctype = null
    locator = null
    parser = null
    return ret!!
  }

  override fun setDocumentLocator(l: Locator?) {
    locator = l
  }

  open fun getDOMImplementation(ver: String?) = implementation

  override fun fatalError(ex: SAXParseException) = throw ex

  override fun error(ex: SAXParseException) = throw ex

  override fun warning(ex: SAXParseException?) {
  }

  override fun startDocument() {
    preInfo = LinkedList()
    namespaces = HashTableStack()
    val namespaces = namespaces!!
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

    stringBuffer.setLength(0)
    stringContent = false

    if (createDocumentDescriptor) {
      documentDescriptor = DocumentDescriptor()
    }
    else {
      documentDescriptor = null
    }
  }

  override fun startElement(uri: String?, localName: String?, rawName: String?, attributes: Attributes?) {
    // Check If we should halt early.
    if (HaltingThread.hasBeenHalted()) {
      throw SAXException(InterruptedIOException())
    }

    if (inProlog) {
      inProlog = false
      if (parser != null) {
        try {
          isStandalone = parser!!.getFeature("http://xml.org/sax/features/is-standalone")
        }
        catch (ex: SAXNotRecognizedException) {
        }

        try {
          xmlVersion = parser!!.getProperty("http://xml.org/sax/properties/document-xml-version") as String
        }
        catch (ex: SAXNotRecognizedException) {
        }

      }
    }

    // Namespaces resolution
    val len = attributes!!.length
    namespaces!!.push()
    var version: String? = null
    for (i in 0 until len) {
      val qName = attributes.getQName(i)
      val sLen = qName.length
      if (sLen < 5)
        continue
      if (qName == "version") {
        version = attributes.getValue(i)
        continue
      }
      if (!qName.startsWith("xmlns"))
        continue
      if (sLen == 5) {
        var ns: String? = attributes.getValue(i)
        if (ns!!.isEmpty()) {
          ns = null
        }
        namespaces!!.put("", ns)
      }
      else if (qName[5] == ':') {
        var ns: String? = attributes.getValue(i)
        if (ns!!.isEmpty()) {
          ns = null
        }
        namespaces!!.put(qName.substring(6), ns)
      }
    }

    // Add any collected String Data before element.
    appendStringData()

    // Element creation
    val e: Element
    var idx = rawName!!.indexOf(':')
    val nsp = when (idx) {
      -1, rawName.length - 1 -> ""
      else -> rawName.substring(0, idx)
    }

    var nsURI: String? = namespaces!!.get(nsp)
    if (currentNode == null) {
      implementation = getDOMImplementation(version)
      document = implementation.createDocument(nsURI, rawName, doctype)
      val i = preInfo!!.iterator()
      e = document!!.documentElement
      currentNode = e
      while (i.hasNext()) {
        val pi = i.next() as PreInfo
        val n = pi.createNode(document!!)
        document!!.insertBefore(n, e)
      }
      preInfo = null
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
    for (i in 0 until len) {
      val qName = attributes.getQName(i)
      if (qName == "xmlns") {
        e.setAttributeNS(XMLSupport.XMLNS_NAMESPACE_URI, qName, attributes.getValue(i))
      }
      else {
        idx = qName.indexOf(':')
        nsURI = when (idx) {
          -1 -> null
          else -> namespaces!!.get(qName.substring(0, idx))
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
    namespaces!!.pop()
  }

  private fun appendStringData() {
    if (!stringContent) {
      return
    }

    val str = stringBuffer.toString()
    stringBuffer.setLength(0) // reuse buffer.
    stringContent = false
    if (currentNode == null) {
      when {
        inCDATA -> preInfo!!.add(CDataInfo(str))
        else -> preInfo!!.add(TextInfo(str))
      }
    }
    else {
      val n: Node
      when {
        inCDATA -> n = document!!.createCDATASection(str)
        else -> n = document!!.createTextNode(str)
      }
      currentNode!!.appendChild(n)
    }
  }

  override fun characters(ch: CharArray?, start: Int, length: Int) {
    stringBuffer.append(ch, start, length)
    stringContent = true
  }

  override fun ignorableWhitespace(ch: CharArray?, start: Int, length: Int) {
    stringBuffer.append(ch, start, length)
    stringContent = true
  }

  override fun processingInstruction(target: String?, data: String?) {
    if (inDTD) {
      return
    }

    appendStringData() // Add any collected String Data before PI

    when (currentNode) {
      null -> preInfo!!.add(ProcessingInstructionInfo(target, data))
      else -> currentNode!!.appendChild(document!!.createProcessingInstruction(target, data))
    }
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
    if (inDTD) return
    appendStringData()

    val str = String(ch, start, length)
    if (currentNode == null) {
      preInfo!!.add(CommentInfo(str))
    }
    else {
      currentNode!!.appendChild(document!!.createComment(str))
    }
  }
}