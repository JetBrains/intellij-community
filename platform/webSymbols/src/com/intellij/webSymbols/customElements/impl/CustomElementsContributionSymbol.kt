// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsSymbol
import com.intellij.webSymbols.customElements.json.CustomElementsContribution
import com.intellij.webSymbols.customElements.json.mapToReferenceList
import com.intellij.webSymbols.customElements.json.toApiStatus
import com.intellij.webSymbols.impl.StaticWebSymbolsScopeBase
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

abstract class CustomElementsContributionSymbol<T : CustomElementsContribution> protected constructor(
  final override val name: String,
  protected val contribution: T,
  final override val origin: CustomElementsJsonOrigin,
) : CustomElementsSymbol, StaticWebSymbolsScopeBase.StaticSymbolContributionAdapter {

  final override val pattern: WebSymbolsPattern?
    get() = null

  final override val framework: FrameworkId?
    get() = null

  final override val apiStatus: WebSymbolApiStatus
    get() = contribution.deprecated.toApiStatus(origin) ?: WebSymbolApiStatus.Stable

  final override val description: String?
    get() = (contribution.description?.takeIf { it.isNotBlank() } ?: contribution.summary)
      ?.let { origin.renderDescription(it) }

  override val type: Any?
    get() = contribution.type?.let { origin.typeSupport?.resolve(it.mapToReferenceList()) }

  final override fun withQueryExecutorContext(queryExecutor: WebSymbolsQueryExecutor): WebSymbol =
    this

  override fun createPointer(): Pointer<out CustomElementsContributionSymbol<out T>> =
    Pointer.hardPointer(this)

}