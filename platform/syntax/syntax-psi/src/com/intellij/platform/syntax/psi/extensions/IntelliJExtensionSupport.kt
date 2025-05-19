// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.extensions

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.ExtensionSupport
import com.intellij.platform.syntax.extensions.SyntaxLanguage
import java.util.concurrent.ConcurrentHashMap

/**
 * IntelliJ backend for Syntax's [com.intellij.platform.syntax.extensions.ExtensionSupport]. Delegates to IntelliJ plugin model.
 *
 * @see com.intellij.platform.syntax.extensions.ExtensionSupport
 */
internal class IntelliJExtensionSupport : ExtensionSupport {
  private val languageExtensionCache = ConcurrentHashMap<ExtensionPointKey<*>, LanguageExtension<*>>()

  override fun <T : Any> getExtensions(extensionPoint: ExtensionPointKey<T>): List<T> {
    return ExtensionPointName<T>(extensionPoint.name).extensionList
  }

  override fun <T : Any> getLanguageExtensions(extensionPoint: ExtensionPointKey<T>, language: SyntaxLanguage): List<T> {
    val languageExtension = languageExtensionCache.computeIfAbsent(extensionPoint) { ext -> LanguageExtension<T>(ext.name) } as LanguageExtension<T>
    val ijLanguage = language.asIntelliJLanguage()
    return languageExtension.allForLanguage(ijLanguage)
  }
}