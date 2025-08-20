// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.util.language

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.currentExtensionSupport
import org.jetbrains.annotations.ApiStatus

/**
 * Specifies which languages a given element type belongs to.
 *
 * This information is used by [com.intellij.platform.syntax.tree.SyntaxNode] to determine the language of the node.
 * If the language provider is missing or does not provide a single language for the given element type, the node will use the language of its parent.
 * If the language provider is missing or does not provide a single language even for the parent, the language of the parser will be used.
 *
 * So you need to provide the languages for your language only if the given node tree contains several languages at once.
 *
 * You can register your own [SyntaxElementLanguageProvider] in [ExtensionSupport] via `com.intellij.syntax.syntaxElementLanguageProvider` extension point.
 * N.B. There may be several language providers registered, in which case they are combined.
 *
 * @see getLanguage
 * @see syntaxElementLanguageProvider
 * @see FiniteSyntaxElementLanguageProvider
 */
@ApiStatus.Experimental
fun interface SyntaxElementLanguageProvider {
  fun getLanguages(elementType: SyntaxElementType): Sequence<SyntaxLanguage>
}

/**
 * Returns the language that the given element type belongs to or `null` if it doesn't belong to any language or if several languages are possible.
 */
@ApiStatus.Experimental
fun SyntaxElementLanguageProvider.getLanguage(elementType: SyntaxElementType): SyntaxLanguage? =
  getLanguages(elementType).singleOrNull()

/**
 * Returns a [SyntaxElementLanguageProvider] from the current [ExtensionSupport].
 * Note that if several language providers are registered, the result provider combines them all.
 */
@ApiStatus.Experimental
fun syntaxElementLanguageProvider(): SyntaxElementLanguageProvider? {
  val languageProviders = currentExtensionSupport().getExtensions(syntaxElementLanguageProviderEP)
  if (languageProviders.isEmpty()) return null

  if (languageProviders.size == 1) return languageProviders[0]

  // todo implement a more efficient way to combine FiniteSyntaxElementLanguageProviders
  return SyntaxElementLanguageProvider { elementType ->
    languageProviders.asSequence().flatMap { languageProvider ->
      languageProvider.getLanguages(elementType)
    }
  }
}

/**
 * A [SyntaxElementLanguageProvider] that returns the given language for all element types in the given set.
 * Prefer this class over implementing your own [SyntaxElementLanguageProvider] if the set of element types is finite.
 *
 * @see SyntaxElementLanguageProvider
 */
@ApiStatus.Experimental
class FiniteSyntaxElementLanguageProvider(
  private val language: SyntaxLanguage,
  private val elementTypes: SyntaxElementTypeSet,
) : SyntaxElementLanguageProvider {
  override fun getLanguages(elementType: SyntaxElementType): Sequence<SyntaxLanguage> {
    return if (elementType in elementTypes) {
      sequenceOf(language)
    }
    else {
      emptySequence()
    }
  }
}

private val syntaxElementLanguageProviderEP = ExtensionPointKey<SyntaxElementLanguageProvider>("com.intellij.syntax.syntaxElementLanguageProvider")
