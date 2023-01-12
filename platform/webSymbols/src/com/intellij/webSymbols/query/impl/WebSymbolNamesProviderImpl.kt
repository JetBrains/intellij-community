// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.webSymbols.*
import com.intellij.webSymbols.framework.WebSymbolsFramework
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolNameConverter
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.query.WebSymbolNamesProvider.Target.*
import java.util.*

internal class WebSymbolNamesProviderImpl(
  private val framework: FrameworkId?,
  private val configuration: List<WebSymbolNameConversionRules>,
  private val modificationTracker: ModificationTracker,
) : WebSymbolNamesProvider {

  private val canonicalNamesProviders: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  private val matchNamesProviders: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  private val nameVariantsProviders: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>

  private val webSymbolsFramework get() = framework?.let { WebSymbolsFramework.get(it) }

  init {
    val canonicalNamesProviders = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
    val matchNamesProviders = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
    val nameVariantsProviders = mutableMapOf<WebSymbolQualifiedKind, WebSymbolNameConverter>()
    configuration.forEach { config ->
      config.canonicalNames.forEach { canonicalNamesProviders.putIfAbsent(it.key, it.value) }
      config.matchNames.forEach { matchNamesProviders.putIfAbsent(it.key, it.value) }
      config.nameVariants.forEach { nameVariantsProviders.putIfAbsent(it.key, it.value) }
    }
    this.canonicalNamesProviders = canonicalNamesProviders
    this.matchNamesProviders = matchNamesProviders
    this.nameVariantsProviders = nameVariantsProviders
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

  override fun getNames(namespace: SymbolNamespace,
                        kind: SymbolKind,
                        name: String,
                        target: WebSymbolNamesProvider.Target): List<String> =
    webSymbolsFramework?.getNames(namespace, kind, name, target)?.takeIf { it.isNotEmpty() }
    ?: when (target) {
      CODE_COMPLETION_VARIANTS -> {
        nameVariantsProviders[WebSymbolQualifiedKind(namespace, kind)]?.getNames(name)
        ?: listOf(name)
      }
      NAMES_MAP_STORAGE -> {
        canonicalNamesProviders[WebSymbolQualifiedKind(namespace, kind)]?.getNames(name)
      }
      NAMES_QUERY -> {
        (matchNamesProviders[WebSymbolQualifiedKind(namespace, kind)]
         ?: canonicalNamesProviders[WebSymbolQualifiedKind(namespace, kind)])
          ?.getNames(name)
      }
    }
    ?: listOf(
      if (namespace == WebSymbol.NAMESPACE_CSS || namespace == WebSymbol.NAMESPACE_HTML)
        name.lowercase(Locale.US)
      else name)

  override fun adjustRename(namespace: SymbolNamespace,
                            kind: SymbolKind,
                            oldName: String,
                            newName: String,
                            occurence: String): String {
    if (oldName == occurence) return newName

    val oldVariants = getNames(namespace, kind, oldName, WebSymbolNamesProvider.Target.NAMES_QUERY)
    val index = oldVariants.indexOf(occurence)

    if (index < 0) return newName

    val newVariants = getNames(namespace, kind, newName, WebSymbolNamesProvider.Target.NAMES_QUERY)

    if (oldVariants.size == newVariants.size)
      return newVariants[index]

    return newName
  }

}