package com.intellij.webSymbols.registry.impl

import com.intellij.webSymbols.impl.WebSymbolNamesProviderImpl
import com.intellij.webSymbols.impl.WebSymbolsRegistryImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.*

class WebSymbolsMockRegistryManager : WebSymbolsRegistryManager {

  private val contributionsContainers = mutableListOf<WebSymbolsContainer>()

  var framework: String? = null

  override fun get(contextElement: PsiElement?, allowResolve: Boolean): WebSymbolsRegistry =
    WebSymbolsRegistryImpl(contributionsContainers,
                           WebSymbolNamesProviderImpl(framework, contributionsContainers.filterIsInstance<WebFrameworksConfiguration>()),
                           WebSymbolsScopeProvider.getScope(contextElement, framework), framework, allowResolve)

  override fun addSymbolsContainer(container: WebSymbolsContainer,
                                   contextDirectory: VirtualFile?,
                                   disposable: Disposable) {
    contributionsContainers.add(container)
  }

  override fun dispose() {
  }

}