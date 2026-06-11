// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.ExtensionRegistry

/**
 * Simple [ExtensionSupport] backend that is used outside IntelliJ runtime.
 * Threadsafe.
 */
internal class ExtensionRegistryImpl : ExtensionRegistry {
  private val ourExtensions: MultiplatformConcurrentMultiMap<ExtensionPointKey<*>, Any> = newConcurrentMultiMap()
  private val ourLanguageExtensions: MultiplatformConcurrentMap<ExtensionPointKey<*>, MultiplatformConcurrentMultiMap<SyntaxLanguage, Any>> = newConcurrentMap()

  override fun <T : Any> registerExtension(extensionPoint: ExtensionPointKey<T>, extension: T) {
    ourExtensions.putValue(extensionPoint, extension)
  }

  override fun <T : Any> unregisterExtension(extensionPoint: ExtensionPointKey<T>, extension: T) {
    ourExtensions.remove(extensionPoint, extension)
  }

  override fun <T : Any> getExtensions(extensionPoint: ExtensionPointKey<T>): Sequence<T> {
    @Suppress("UNCHECKED_CAST")
    return ourExtensions.get(extensionPoint).asSequence() as Sequence<T>
  }

  override fun <T : Any> registerLanguageExtension(extensionPoint: ExtensionPointKey<T>, extension: T, language: SyntaxLanguage) {
    val extensions = ourLanguageExtensions.computeIfAbsent(extensionPoint) { _ -> newConcurrentMultiMap() }
    extensions.putValue(language, extension)
  }

  override fun <T : Any> unregisterLanguageExtension(extensionPoint: ExtensionPointKey<T>, language: SyntaxLanguage) {
    val extensions = ourLanguageExtensions.get(extensionPoint)
    extensions?.remove(language)
  }

  override fun <T : Any> getLanguageExtensions(extensionPoint: ExtensionPointKey<T>, language: SyntaxLanguage): Sequence<T> {
    val extensionMap = ourLanguageExtensions.get(extensionPoint) ?: return emptySequence()
    @Suppress("UNCHECKED_CAST")
    return extensionMap.get(language).asSequence() as Sequence<T>
  }
}