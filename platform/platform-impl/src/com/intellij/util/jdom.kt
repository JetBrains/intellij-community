/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.reference.SoftReference
import com.intellij.util.text.CharSequenceReader
import org.jdom.Document
import org.jdom.input.SAXBuilder
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.CharArrayReader
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

private val cachedSaxBuilder = ThreadLocal<SoftReference<SAXBuilder>>()

private fun getSaxBuilder(): SAXBuilder {
  val reference = cachedSaxBuilder.get()
  var saxBuilder = SoftReference.dereference<SAXBuilder>(reference)
  if (saxBuilder == null) {
    saxBuilder = SAXBuilder()
    saxBuilder.ignoringBoundaryWhitespace = true
    saxBuilder.ignoringElementContentWhitespace = true
    saxBuilder.entityResolver = EntityResolver { publicId, systemId -> InputSource(CharArrayReader(ArrayUtil.EMPTY_CHAR_ARRAY)) }
    cachedSaxBuilder.set(SoftReference<SAXBuilder>(saxBuilder))
  }
  return saxBuilder
}

fun loadElement(chars: CharSequence) = loadDocument(CharSequenceReader(chars)).detachRootElement()

fun loadElement(path: Path) = loadDocument(Files.newInputStream(path).bufferedReader()).detachRootElement()

private fun loadDocument(reader: Reader): Document {
  if (Registry.`is`("jdom.ignoring.whitespace", false) || (ApplicationManager.getApplication()?.isUnitTestMode ?: false)) {
    try {
      return getSaxBuilder().build(reader)
    }
    finally {
      reader.close()
    }
  }
  else {
    return JDOMUtil.loadDocument(reader)
  }
}