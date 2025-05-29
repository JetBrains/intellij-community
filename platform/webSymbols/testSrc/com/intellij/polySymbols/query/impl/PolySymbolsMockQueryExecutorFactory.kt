package com.intellij.polySymbols.query.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.query.PolySymbolsQueryExecutorFactory
import com.intellij.polySymbols.query.PolySymbolsQueryResultsCustomizerFactory
import com.intellij.polySymbols.utils.createModificationTracker

class PolySymbolsMockQueryExecutorFactory : PolySymbolsQueryExecutorFactory {

  private val scopeList = mutableListOf<PolySymbolsScope>()

  val context: MutableMap<PolyContextKind, PolyContextName> = mutableMapOf()

  override fun create(location: PsiElement?, allowResolve: Boolean): PolySymbolsQueryExecutor =
    PolySymbolsQueryExecutorImpl(location, scopeList,
                                 PolySymbolNamesProviderImpl(
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