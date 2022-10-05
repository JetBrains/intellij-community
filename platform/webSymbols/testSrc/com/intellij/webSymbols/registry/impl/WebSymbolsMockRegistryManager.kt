package com.intellij.webSymbols.registry.impl

import com.intellij.webSymbols.impl.WebSymbolNamesProviderImpl
import com.intellij.webSymbols.impl.WebSymbolsRegistryImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.*
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.context.WebSymbolsContextKindRules

class WebSymbolsMockRegistryManager : WebSymbolsRegistryManager {

  private val contributionsContainers = mutableListOf<WebSymbolsContainer>()

  val context: MutableMap<ContextKind, ContextName> = mutableMapOf()

  override fun get(location: PsiElement?, allowResolve: Boolean): WebSymbolsRegistry =
    WebSymbolsRegistryImpl(contributionsContainers,
                           WebSymbolNamesProviderImpl(context[KIND_FRAMEWORK], contributionsContainers.filterIsInstance<WebSymbolNameConversionRules>()),
                           WebSymbolsScopeProvider.getScope(location, WebSymbolsContext.create(context)),
                           WebSymbolsContext.create(context),
                           allowResolve)

  override fun addSymbolsContainer(container: WebSymbolsContainer,
                                   contextDirectory: VirtualFile?,
                                   disposable: Disposable) {
    contributionsContainers.add(container)
  }

  override fun dispose() {
  }

}