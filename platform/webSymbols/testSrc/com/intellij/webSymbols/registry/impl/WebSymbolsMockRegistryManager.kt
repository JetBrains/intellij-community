package com.intellij.webSymbols.registry.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.WebSymbolsContainer
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.registry.WebSymbolsRegistry
import com.intellij.webSymbols.registry.WebSymbolsRegistryManager
import com.intellij.webSymbols.registry.WebSymbolsScopeProvider
import com.intellij.webSymbols.utils.createModificationTracker

class WebSymbolsMockRegistryManager : WebSymbolsRegistryManager {

  private val contributionsContainers = mutableListOf<WebSymbolsContainer>()

  val context: MutableMap<ContextKind, ContextName> = mutableMapOf()

  override fun get(location: PsiElement?, allowResolve: Boolean): WebSymbolsRegistry =
    WebSymbolsRegistryImpl(contributionsContainers,
                           WebSymbolNamesProviderImpl(
                             context[KIND_FRAMEWORK],
                             context[KIND_FRAMEWORK]?.let { framework ->
                               contributionsContainers.filterIsInstance<WebTypesMockContainerImpl>().map {
                                 it.getNameConversionRulesProvider(framework).getNameConversionRules()
                               }
                             } ?: emptyList(),
                             createModificationTracker(
                               contributionsContainers.filterIsInstance<WebTypesMockContainerImpl>().map { it.createPointer() })),
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