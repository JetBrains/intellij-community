// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsSymbol
import com.intellij.polySymbols.customElements.json.CustomElementsContribution
import com.intellij.polySymbols.customElements.json.mapToReferenceList
import com.intellij.polySymbols.customElements.json.toApiStatus
import com.intellij.polySymbols.impl.StaticPolySymbolScopeBase
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.PolySymbolQueryExecutor

abstract class CustomElementsContributionSymbol<T : CustomElementsContribution> protected constructor(
  final override val name: String,
  protected val contribution: T,
  final override val origin: CustomElementsJsonOrigin,
) : CustomElementsSymbol, StaticPolySymbolScopeBase.StaticSymbolContributionAdapter {

  final override val pattern: PolySymbolPattern?
    get() = null

  final override val framework: FrameworkId?
    get() = null

  final override val apiStatus: PolySymbolApiStatus
    get() = contribution.deprecated.toApiStatus(origin) ?: PolySymbolApiStatus.Stable

  final override val description: String?
    get() = (contribution.description?.takeIf { it.isNotBlank() } ?: contribution.summary)
      ?.let { origin.renderDescription(it) }

  open val type: Any?
    get() = contribution.type?.let { origin.typeSupport?.resolve(it.mapToReferenceList()) }

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
    when (property) {
      origin.typeSupport?.typeProperty -> property.tryCast(type)
      else -> super<CustomElementsSymbol>.get(property)
    }

  final override fun withQueryExecutorContext(queryExecutor: PolySymbolQueryExecutor): PolySymbol =
    this

  override fun matchContext(context: PolyContext): Boolean =
    super<CustomElementsSymbol>.matchContext(context)

  override fun createPointer(): Pointer<out CustomElementsContributionSymbol<out T>> =
    Pointer.hardPointer(this)

}