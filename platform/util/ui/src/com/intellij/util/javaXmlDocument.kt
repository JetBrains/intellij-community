// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Internal
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Use only and only if you cannot avoid using of JVM document
 */
@Internal
@JvmOverloads
fun createDocumentBuilder(
  namespaceAware: Boolean = false,
  allowDoctype: Boolean = false,
  ignoreComments: Boolean = false,
): DocumentBuilder {
  return try {
    createDocumentBuilderFactory(namespaceAware = namespaceAware, allowDoctype = allowDoctype, ignoreComments = ignoreComments).newDocumentBuilder()
  }
  catch (e: Throwable) {
    throw IllegalStateException("Unable to create DOM parser", e)
  }
}

/**
 * Use only and only if you cannot avoid using of JVM document
 */
@Suppress("HttpUrlsUsage")
@Internal
@JvmOverloads
fun createDocumentBuilderFactory(
  namespaceAware: Boolean = false,
  allowDoctype: Boolean = false,
  ignoreComments: Boolean = false,
): DocumentBuilderFactory {
  val factory = DocumentBuilderFactory.newDefaultInstance()
  factory.isNamespaceAware = namespaceAware
  factory.isValidating = false
  factory.isIgnoringComments = ignoreComments

  factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)

  if (!allowDoctype) {
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
  }

  factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
  factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)

  factory.setFeature("http://xml.org/sax/features/validation", false)
  factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
  factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

  factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
  factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")

  factory.isXIncludeAware = false
  factory.isExpandEntityReferences = false

  return factory
}
