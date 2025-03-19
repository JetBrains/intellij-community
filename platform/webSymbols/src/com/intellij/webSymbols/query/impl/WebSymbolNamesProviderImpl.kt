// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.framework.WebSymbolsFramework
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolNameConverter
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.*
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class WebSymbolNamesProviderImpl(
  private val framework: FrameworkId?,
  private val configuration: List<WebSymbolNameConversionRules>,
  private val modificationTracker: ModificationTracker,
) : WebSymbolNamesProvider {

  private val canonicalNamesProviders: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  private val matchNamesProviders: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  private val completionVariantsProviders: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  private val renameProviders: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  private val webSymbolsFramework get() = framework?.let { WebSymbolsFramework.get(it) }

  init {
    val canonicalNamesProviders = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
    val matchNamesProviders = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
    val completionVariantsProviders = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
    val renameProviders = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
    configuration.forEach { config ->
      config.canonicalNames.forEach { canonicalNamesProviders.putIfAbsent(it.key, it.value) }
      config.matchNames.forEach { matchNamesProviders.putIfAbsent(it.key, it.value) }
      config.completionVariants.forEach { completionVariantsProviders.putIfAbsent(it.key, it.value) }
      config.renames.forEach { renameProviders.putIfAbsent(it.key, it.value) }
    }
    this.canonicalNamesProviders = canonicalNamesProviders
    this.matchNamesProviders = matchNamesProviders
    this.completionVariantsProviders = completionVariantsProviders
    this.renameProviders = renameProviders
  }

  override fun createPointer(): Pointer<WebSymbolNamesProvider> =
    Pointer.hardPointer(this)

  override fun hashCode(): Int =
    Objects.hash(framework, configuration)

  override fun equals(other: Any?): Boolean =
    other is WebSymbolNamesProviderImpl
    && other.framework == framework
    && other.configuration == configuration

  override fun getModificationCount(): Long =
    modificationTracker.modificationCount

  override fun withRules(rules: List<WebSymbolNameConversionRules>): WebSymbolNamesProvider =
    WebSymbolNamesProviderImpl(framework, rules + configuration, modificationTracker)

  override fun getNames(qualifiedName: WebSymbolQualifiedName, target: WebSymbolNamesProvider.Target): List<String> =
    when (target) {
      CODE_COMPLETION_VARIANTS -> completionVariantsProviders[qualifiedName.qualifiedKind]
      NAMES_MAP_STORAGE -> canonicalNamesProviders[qualifiedName.qualifiedKind]
      NAMES_QUERY -> matchNamesProviders[qualifiedName.qualifiedKind]
                     ?: canonicalNamesProviders[qualifiedName.qualifiedKind]
      RENAME_QUERY -> renameProviders[qualifiedName.qualifiedKind]
                      ?: matchNamesProviders[qualifiedName.qualifiedKind]
                      ?: canonicalNamesProviders[qualifiedName.qualifiedKind]
    }
      ?.getNames(qualifiedName.name)
    ?: webSymbolsFramework
      ?.getNames(qualifiedName, target)
      ?.takeIf { it.isNotEmpty() }
    ?: if (target != CODE_COMPLETION_VARIANTS &&
           (qualifiedName.namespace == WebSymbol.NAMESPACE_CSS || qualifiedName.namespace == WebSymbol.NAMESPACE_HTML))
      listOf(qualifiedName.name.lowercase(Locale.US))
    else
      listOf(qualifiedName.name)

  override fun adjustRename(
    qualifiedName: WebSymbolQualifiedName,
    newName: String,
    occurence: String,
  ): String {
    if (qualifiedName.name == occurence) return newName

    val oldVariants = getNames(qualifiedName, RENAME_QUERY)
    val index = oldVariants.indexOf(occurence)

    if (index < 0) return newName

    val newVariants = getNames(qualifiedName.copy(name = newName), RENAME_QUERY)

    if (oldVariants.size == newVariants.size)
      return newVariants[index]

    return newName
  }

}