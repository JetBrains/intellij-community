// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.WebSymbolNameConversionRules
import com.intellij.webSymbols.WebSymbolNamesProvider
import com.intellij.webSymbols.WebSymbolNamesProvider.Target.*
import com.intellij.webSymbols.WebSymbolsContainer.Namespace
import com.intellij.webSymbols.framework.WebFramework
import com.intellij.webSymbols.framework.WebFrameworksConfiguration
import java.util.*
import java.util.function.Function

internal class WebSymbolNamesProviderImpl(
  private val framework: FrameworkId?,
  private val configuration: List<WebSymbolNameConversionRules>,
) : WebSymbolNamesProvider {

  private val canonicalNamesProviders: Map<Triple<FrameworkId?, Namespace, SymbolKind>,
    Function<String, List<String>>>

  private val matchNamesProviders: Map<Triple<FrameworkId?, Namespace, SymbolKind>,
    Function<String, List<String>>>

  private val nameVariantsProviders: Map<Triple<FrameworkId?, Namespace, SymbolKind>,
    Function<String, List<String>>>

  private val webFramework get() = framework?.let { WebFramework.get(it) }

  init {
    val canonicalNamesProviders = mutableMapOf<Triple<FrameworkId?, Namespace, SymbolKind>,
      Function<String, List<String>>>()
    val matchNamesProviders = mutableMapOf<Triple<FrameworkId?, Namespace, SymbolKind>,
      Function<String, List<String>>>()
    val nameVariantsProviders = mutableMapOf<Triple<FrameworkId?, Namespace, SymbolKind>,
      Function<String, List<String>>>()
    configuration.forEach { config ->
      config.canonicalNamesProviders.forEach { canonicalNamesProviders.putIfAbsent(it.key, it.value) }
      config.matchNamesProviders.forEach { matchNamesProviders.putIfAbsent(it.key, it.value) }
      config.nameVariantsProviders.forEach { nameVariantsProviders.putIfAbsent(it.key, it.value) }
    }
    this.canonicalNamesProviders = canonicalNamesProviders
    this.matchNamesProviders = matchNamesProviders
    this.nameVariantsProviders = nameVariantsProviders
  }

  override fun createPointer(): Pointer<WebSymbolNamesProvider> {
    val configuration = this.configuration.map { it.createPointer() }
    val framework = this.framework
    return Pointer {
      @Suppress("UNCHECKED_CAST")
      val newConfiguration = configuration.map { it.dereference() }
                               .takeIf { it.all { config -> config != null } }
                               as? List<WebFrameworksConfiguration>
                             ?: return@Pointer null
      WebSymbolNamesProviderImpl(framework, newConfiguration)
    }
  }

  override fun hashCode(): Int =
    Objects.hash(framework, configuration)

  override fun equals(other: Any?): Boolean =
    other is WebSymbolNamesProviderImpl
    && other.framework == framework
    && other.configuration == configuration

  override fun getModificationCount(): Long =
    configuration.sumOf { it.modificationCount }

  override fun withRules(rules: List<WebSymbolNameConversionRules>): WebSymbolNamesProvider =
    WebSymbolNamesProviderImpl(framework, rules + configuration)

  override fun getNames(namespace: Namespace,
                        kind: SymbolKind,
                        name: String,
                        target: WebSymbolNamesProvider.Target): List<String> =
    webFramework?.getNames(namespace, kind, name, target)?.takeIf { it.isNotEmpty() }
    ?: when (target) {
      CODE_COMPLETION_VARIANTS -> {
        nameVariantsProviders[Triple(framework, namespace, kind)]?.apply(name)
        ?: listOf(name)
      }
      NAMES_MAP_STORAGE -> {
        canonicalNamesProviders[Triple(framework, namespace, kind)]?.apply(name)
      }
      NAMES_QUERY -> {
        (matchNamesProviders[Triple(framework, namespace, kind)]
         ?: canonicalNamesProviders[Triple(framework, namespace, kind)])
          ?.apply(name)
      }
    }
    ?: listOf(
      if (namespace == Namespace.JS)
        name
      else
        name.lowercase(Locale.US))

  override fun adjustRename(namespace: Namespace,
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