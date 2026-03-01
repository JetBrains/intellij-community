package com.intellij.polySymbols.query.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.query.PolySymbolQueryResultsCustomizerFactory
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.utils.createModificationTracker
import com.intellij.psi.PsiElement

class PolySymbolMockQueryExecutorFactory : PolySymbolQueryExecutorFactory {

  private val scopeList = mutableListOf<PolySymbolScope>()

  val context: MutableMap<PolyContextKind, PolyContextName> = mutableMapOf()

  override fun create(location: PsiElement?, allowResolve: Boolean): PolySymbolQueryExecutor {
    val polyContext = PolyContext.create(context)
    return PolySymbolQueryExecutorImpl(location, scopeList,
                                       PolySymbolNamesProviderImpl(
                                         polyContext,
                                         scopeList.filterIsInstance<WebTypesMockScopeImpl>().mapNotNull {
                                           it.getNameConversionRulesProvider(polyContext)?.getNameConversionRules()
                                         },
                                         createModificationTracker(
                                           scopeList.filterIsInstance<WebTypesMockScopeImpl>().map { it.createPointer() })),
                                       PolySymbolQueryResultsCustomizerFactory.getQueryResultsCustomizer(location,
                                                                                                         PolyContext.create(context)),
                                       PolyContext.create(context),
                                       allowResolve)
  }

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