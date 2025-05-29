package com.intellij.webSymbols.query.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.context.PolyContext
import com.intellij.webSymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.query.PolySymbolsQueryExecutor
import com.intellij.webSymbols.query.PolySymbolsQueryExecutorFactory
import com.intellij.webSymbols.query.PolySymbolsQueryResultsCustomizerFactory
import com.intellij.webSymbols.utils.createModificationTracker

class PolySymbolsMockQueryExecutorFactory : PolySymbolsQueryExecutorFactory {

  private val scopeList = mutableListOf<PolySymbolsScope>()

  val context: MutableMap<ContextKind, ContextName> = mutableMapOf()

  override fun create(location: PsiElement?, allowResolve: Boolean): PolySymbolsQueryExecutor =
    PolySymbolsQueryExecutorImpl(location, scopeList,
                                 WebSymbolNamesProviderImpl(
                                  context[KIND_FRAMEWORK],
                                  context[KIND_FRAMEWORK]?.let { framework ->
                                    scopeList.filterIsInstance<WebTypesMockScopeImpl>().map {
                                      it.getNameConversionRulesProvider(framework).getNameConversionRules()
                                    }
                                  } ?: emptyList(),
                                  createModificationTracker(
                                    scopeList.filterIsInstance<WebTypesMockScopeImpl>().map { it.createPointer() })),
                                 PolySymbolsQueryResultsCustomizerFactory.getQueryResultsCustomizer(location, PolyContext.create(context)),
                                 PolyContext.create(context),
                                 allowResolve)

  override fun addScope(
    scope: PolySymbolsScope,
    contextDirectory: VirtualFile?,
    disposable: Disposable,
  ) {
    scopeList.add(scope)
  }

  override fun dispose() {
  }

}