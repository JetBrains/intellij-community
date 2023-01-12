package com.intellij.webSymbols.query.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.query.WebSymbolsQueryResultsCustomizerFactory
import com.intellij.webSymbols.utils.createModificationTracker

class WebSymbolsMockQueryExecutorFactory : WebSymbolsQueryExecutorFactory {

  private val scopeList = mutableListOf<WebSymbolsScope>()

  val context: MutableMap<ContextKind, ContextName> = mutableMapOf()

  override fun create(location: PsiElement?, allowResolve: Boolean): WebSymbolsQueryExecutor =
    WebSymbolsQueryExecutorImpl(scopeList,
                                WebSymbolNamesProviderImpl(
                             context[KIND_FRAMEWORK],
                             context[KIND_FRAMEWORK]?.let { framework ->
                               scopeList.filterIsInstance<WebTypesMockScopeImpl>().map {
                                 it.getNameConversionRulesProvider(framework).getNameConversionRules()
                               }
                             } ?: emptyList(),
                             createModificationTracker(
                               scopeList.filterIsInstance<WebTypesMockScopeImpl>().map { it.createPointer() })),
                                WebSymbolsQueryResultsCustomizerFactory.getScope(location, WebSymbolsContext.create(context)),
                                WebSymbolsContext.create(context),
                                allowResolve)

  override fun addScope(scope: WebSymbolsScope,
                        contextDirectory: VirtualFile?,
                        disposable: Disposable) {
    scopeList.add(scope)
  }

  override fun dispose() {
  }

}