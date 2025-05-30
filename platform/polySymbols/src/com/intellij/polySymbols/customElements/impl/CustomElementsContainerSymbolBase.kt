// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.util.containers.Stack
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.polySymbols.customElements.json.CustomElementsContribution
import com.intellij.polySymbols.query.PolySymbolsCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolsNameMatchQueryParams
import java.util.*

abstract class CustomElementsContainerSymbolBase<Container : CustomElementsContribution> protected constructor(
  name: String,
  container: Container,
  origin: CustomElementsJsonOrigin,
  private val rootScope: CustomElementsManifestScopeBase,
) : CustomElementsContributionSymbol<Container>(name, container, origin) {

  override fun getMatchingSymbols(qualifiedName: PolySymbolQualifiedName,
                                  params: PolySymbolsNameMatchQueryParams,
                                  scope: Stack<PolySymbolsScope>): List<PolySymbol> =
    rootScope
      .getMatchingSymbols(contribution, this.origin, qualifiedName, params, scope)
      .toList()

  override fun getSymbols(qualifiedKind: PolySymbolQualifiedKind,
                          params: PolySymbolsListSymbolsQueryParams,
                          scope: Stack<PolySymbolsScope>): List<PolySymbolsScope> =
    rootScope
      .getSymbols(contribution, this.origin, qualifiedKind, params)
      .toList()

  override fun getCodeCompletions(qualifiedName: PolySymbolQualifiedName,
                                  params: PolySymbolsCodeCompletionQueryParams,
                                  scope: Stack<PolySymbolsScope>): List<PolySymbolCodeCompletionItem> =
    rootScope
      .getCodeCompletions(contribution, this.origin, qualifiedName, params, scope)
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

  override fun hashCode(): Int =
    Objects.hash(name, origin, rootScope, contribution)

}