package com.intellij.polySymbols.query.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.query.PolySymbolQueryResultsCustomizerFactory
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.utils.createModificationTracker
import com.intellij.psi.PsiElement

class PolySymbolMockQueryExecutorFactory : PolySymbolQueryExecutorFactory {

  private val scopeList = mutableListOf<PolySymbolScope>()

  val context: MutableMap<PolyContextKind, PolyContextName> = mutableMapOf()

  override fun create(location: PsiElement?, allowResolve: Boolean): PolySymbolQueryExecutor =
    PolySymbolQueryExecutorImpl(location, scopeList,
                                PolySymbolNamesProviderImpl(
                                   context[KIND_FRAMEWORK],
                                   context[KIND_FRAMEWORK]?.let { framework ->
                                     scopeList.filterIsInstance<WebTypesMockScopeImpl>().map {
                                       it.getNameConversionRulesProvider(framework).getNameConversionRules()
                                     }
                                   } ?: emptyList(),
                                   createModificationTracker(
                                     scopeList.filterIsInstance<WebTypesMockScopeImpl>().map { it.createPointer() })),
                                PolySymbolQueryResultsCustomizerFactory.getQueryResultsCustomizer(location, PolyContext.create(context)),
                                PolyContext.create(context),
                                allowResolve)

  override fun addScope(
    scope: PolySymbolScope,
    contextDirectory: VirtualFile?,
    disposable: Disposable,
  ) {
    scopeList.add(scope)
  }

  override fun dispose() {
  }

}