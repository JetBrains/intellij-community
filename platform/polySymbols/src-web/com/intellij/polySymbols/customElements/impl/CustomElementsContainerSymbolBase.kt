// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.polySymbols.customElements.json.CustomElementsContribution
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack

abstract class CustomElementsContainerSymbolBase<Container : CustomElementsContribution> protected constructor(
  name: String,
  container: Container,
  origin: CustomElementsJsonOrigin,
  private val rootScope: CustomElementsManifestScopeBase,
) : CustomElementsContributionSymbol<Container>(name, container, origin) {

  override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    rootScope
      .getMatchingSymbols(contribution, this.origin, qualifiedName, params, stack)
      .toList()

  override fun getSymbols(
    qualifiedKind: PolySymbolQualifiedKind,
    params: PolySymbolListSymbolsQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    rootScope
      .getSymbols(contribution, this.origin, qualifiedKind, params)
      .toList()

  override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    rootScope
      .getCodeCompletions(contribution, this.origin, qualifiedName, params, stack)
      .toList()

  protected abstract fun getConstructor():
    (String, Container, CustomElementsJsonOrigin, CustomElementsManifestScopeBase) -> CustomElementsContainerSymbolBase<out Container>

  override fun createPointer(): Pointer<out CustomElementsContainerSymbolBase<out Container>> {
    val rootScopePtr = rootScope.createPointer()
    val name = name
    val origin = origin
    val container = contribution
    return Pointer {
      rootScopePtr.dereference()?.let {
        getConstructor()(name, container, origin, rootScope)
      }
    }
  }

  override fun equals(other: Any?): Boolean =
    other is CustomElementsContainerSymbolBase<*>
    && other.name == name
    && other.rootScope == rootScope
    && other.origin == origin
    && other.contribution == contribution

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + origin.hashCode()
    result = 31 * result + rootScope.hashCode()
    result = 31 * result + contribution.hashCode()
    return result
  }

}