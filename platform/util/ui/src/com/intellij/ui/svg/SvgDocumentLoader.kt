// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import org.apache.batik.anim.dom.SVGOMDocument
import org.apache.batik.bridge.DocumentLoader
import org.apache.batik.util.MimeTypeConstants
import org.apache.batik.util.ParsedURL
import org.w3c.dom.Element
import java.io.InputStream

internal object SvgDocumentLoader : DocumentLoader() {
  override fun checkCache(uri: String): SVGOMDocument? = null

  // caller MUST call `userAgent.checkLoadExternalResource` before using this method, we don't perform checking
  override fun loadDocument(uri: String): SVGOMDocument {
    val parsedUrl = ParsedURL(uri)
    val inputStream = parsedUrl.openStream(MimeTypeConstants.MIME_TYPES_SVG_LIST.iterator())
    return createSvgDocument(inputStream = inputStream, uri = uri)
  }

  override fun loadDocument(uri: String, inputStream: InputStream): SVGOMDocument {
    return createSvgDocument(inputStream = inputStream, uri = uri)
  }

  override fun dispose() {
  }

  override fun getLineNumber(e: Element): Int  = -1
}
