// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.model.Pointer
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.webSymbols.customElements.json.CustomElementsContribution
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import java.util.*

abstract class CustomElementsContainerSymbolBase<Container : CustomElementsContribution> protected constructor(
  name: String,
  container: Container,
  origin: CustomElementsJsonOrigin,
  private val rootScope: CustomElementsManifestScopeBase,
) : CustomElementsContributionSymbol<Container>(name, container, origin) {

  override fun getMatchingSymbols(namespace: SymbolNamespace,
                                  kind: String,
                                  name: String,
                                  params: WebSymbolsNameMatchQueryParams,
                                  scope: Stack<WebSymbolsScope>): List<WebSymbol> =
    rootScope
      .getMatchingSymbols(contribution, this.origin, namespace,
                          kind, name, params, scope)
      .toList()

  override fun getSymbols(namespace: SymbolNamespace,
                          kind: SymbolKind,
                          params: WebSymbolsListSymbolsQueryParams,
                          scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    rootScope
      .getSymbols(contribution, this.origin, namespace,
                  kind, params)
      .toList()

  override fun getCodeCompletions(namespace: SymbolNamespace,
                                  kind: String,
                                  name: String,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    rootScope
      .getCodeCompletions(contribution, this.origin, namespace,
                          kind, name, params, scope)
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