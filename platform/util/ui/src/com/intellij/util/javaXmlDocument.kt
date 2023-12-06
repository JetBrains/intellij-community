// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Internal
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

//fun createNamespaceAwareDocumentBuilder(): DocumentBuilder = createDocumentBuilder(namespaceAware = true)

/**
 * Use only and only if you cannot avoid using of JVM document
 */
@Suppress("HttpUrlsUsage")
@Internal
@JvmOverloads
fun createDocumentBuilder(namespaceAware: Boolean = false): DocumentBuilder {
  val factory = DocumentBuilderFactory.newDefaultInstance()
  factory.isNamespaceAware = namespaceAware
  factory.isValidating = false

  factory.setFeature("http://xml.org/sax/features/validation", false)
  factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
  factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
  return factory.newDocumentBuilder()
}