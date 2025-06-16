// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.css.NAMESPACE_CSS
import com.intellij.polySymbols.framework.PolySymbolFramework
import com.intellij.polySymbols.html.NAMESPACE_HTML
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolNameConverter
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolNamesProvider.Target.*
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class PolySymbolNamesProviderImpl(
  private val framework: FrameworkId?,
  private val configuration: List<PolySymbolNameConversionRules>,
  private val modificationTracker: ModificationTracker,
) : PolySymbolNamesProvider {

  private val canonicalNamesProviders: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  private val matchNamesProviders: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  private val completionVariantsProviders: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  private val renameProviders: Map<PolySymbolQualifiedKind, PolySymbolNameConverter>

  private val polySymbolFramework get() = framework?.let { PolySymbolFramework.get(it) }

  init {
    val canonicalNamesProviders = mutableMapOf<PolySymbolQualifiedKind, PolySymbolNameConverter>()
    val matchNamesProviders = mutableMapOf<PolySymbolQualifiedKind, PolySymbolNameConverter>()
    val completionVariantsProviders = mutableMapOf<PolySymbolQualifiedKind, PolySymbolNameConverter>()
    val renameProviders = mutableMapOf<PolySymbolQualifiedKind, PolySymbolNameConverter>()
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

  override fun createPointer(): Pointer<PolySymbolNamesProvider> =
    Pointer.hardPointer(this)

  override fun hashCode(): Int =
    framework.hashCode() * 31 + configuration.hashCode()

  override fun equals(other: Any?): Boolean =
    other is PolySymbolNamesProviderImpl
    && other.framework == framework
    && other.configuration == configuration

  override fun getModificationCount(): Long =
    modificationTracker.modificationCount

  override fun withRules(rules: List<PolySymbolNameConversionRules>): PolySymbolNamesProvider =
    PolySymbolNamesProviderImpl(framework, rules + configuration, modificationTracker)

  override fun getNames(qualifiedName: PolySymbolQualifiedName, target: PolySymbolNamesProvider.Target): List<String> =
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
    ?: polySymbolFramework
      ?.getNames(qualifiedName, target)
      ?.takeIf { it.isNotEmpty() }
    ?: if (target != CODE_COMPLETION_VARIANTS &&
           (qualifiedName.namespace == NAMESPACE_CSS || qualifiedName.namespace == NAMESPACE_HTML))
      listOf(qualifiedName.name.lowercase(Locale.US))
    else
      listOf(qualifiedName.name)

  override fun adjustRename(
    qualifiedName: PolySymbolQualifiedName,
    newName: String,
    occurence: String,
  ): String {
    if (qualifiedName.name == occurence) return newName

    val oldVariants = getNames(qualifiedName, RENAME_QUERY)
    val index = oldVariants.indexOf(occurence)

    if (index < 0) return newName

    val newVariants = getNames(qualifiedName.withName(name = newName), RENAME_QUERY)

    if (oldVariants.size == newVariants.size)
      return newVariants[index]

    return newName
  }

}