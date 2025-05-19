// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.ExtensionRegistry
import com.intellij.platform.syntax.extensions.SyntaxLanguage

/**
 * Simple [ExtensionSupport] backend that is used outside IntelliJ runtime.
 * Threadsafe.
 */
internal object ExtensionRegistryImpl : ExtensionRegistry {
  private val ourExtensions: ConcurrentMultiMap<ExtensionPointKey<*>, Any> = newConcurrentMultiMap()
  private val ourLanguageExtensions: ConcurrentMap<ExtensionPointKey<*>, ConcurrentMultiMap<SyntaxLanguage, Any>> = newConcurrentMap()

  override fun <T : Any> registerExtension(extensionPoint: ExtensionPointKey<T>, extension: T) {
    ourExtensions.putValue(extensionPoint, extension)
  }

  override fun <T : Any> unregisterExtension(extensionPoint: ExtensionPointKey<T>, extension: T) {
    ourExtensions.remove(extensionPoint, extension)
  }

  override fun <T : Any> getExtensions(extensionPoint: ExtensionPointKey<T>): List<T> {
    return ourExtensions.get(extensionPoint).toList() as List<T>
  }

  override fun <T : Any> registerLanguageExtension(extensionPoint: ExtensionPointKey<T>, extension: T, language: SyntaxLanguage) {
    val extensions = ourLanguageExtensions.computeIfAbsent(extensionPoint) { _ -> newConcurrentMultiMap() }
    extensions.putValue(language, extension)
  }

  override fun <T : Any> unregisterLanguageExtension(extensionPoint: ExtensionPointKey<T>, language: SyntaxLanguage) {
    val extensions = ourLanguageExtensions.get(extensionPoint)
    extensions?.remove(language)
  }

  override fun <T : Any> getLanguageExtensions(extensionPoint: ExtensionPointKey<T>, language: SyntaxLanguage): List<T> {
    val extensionMap = ourLanguageExtensions.get(extensionPoint) ?: return emptyList()
    val extensions = extensionMap.get(language) as Set<T>
    return extensions.toList()
  }
}