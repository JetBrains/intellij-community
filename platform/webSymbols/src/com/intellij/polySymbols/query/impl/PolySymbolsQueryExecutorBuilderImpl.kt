// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.ContextKind
import com.intellij.polySymbols.ContextName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.context.impl.PolyContextImpl
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.query.PolySymbolsQueryExecutorFactory.WebSymbolsQueryExecutorBuilder
import com.intellij.polySymbols.query.PolySymbolsQueryResultsCustomizer

class PolySymbolsQueryExecutorBuilderImpl() : WebSymbolsQueryExecutorBuilder {
  private val rootScopes = mutableListOf<PolySymbolsScope>()
  private val customizers = mutableListOf<PolySymbolsQueryResultsCustomizer>()
  private val nameConversionRules = mutableListOf<PolySymbolNameConversionRules>()
  private val context = mutableMapOf<ContextKind, ContextName>()
  private var allowResolve = true

  override fun addRootScope(scope: PolySymbolsScope): WebSymbolsQueryExecutorBuilder = apply {
    rootScopes.add(scope)
  }

  override fun addRootScopes(scope: List<PolySymbolsScope>): WebSymbolsQueryExecutorBuilder = apply {
    rootScopes.addAll(scope)
  }

  override fun addCustomizer(customizer: PolySymbolsQueryResultsCustomizer): WebSymbolsQueryExecutorBuilder = apply {
    customizers.add(customizer)
  }

  override fun addNameConversionRules(rules: PolySymbolNameConversionRules): WebSymbolsQueryExecutorBuilder = apply {
    nameConversionRules.add(rules)
  }

  override fun addWebSymbolsContext(kind: ContextKind, name: ContextName?): WebSymbolsQueryExecutorBuilder = apply {
    if (name == null)
      context.remove(kind)
    else
      context[kind] = name
  }

  override fun setFramework(framework: String): WebSymbolsQueryExecutorBuilder = apply {
    context[KIND_FRAMEWORK] = framework
  }

  override fun allowResolve(allowResolve: Boolean): WebSymbolsQueryExecutorBuilder = apply {
    this.allowResolve = allowResolve
  }

  override fun create(): PolySymbolsQueryExecutor =
    PolySymbolsQueryExecutorImpl(
      null,
      rootScopes,
      PolySymbolNamesProviderImpl(context[KIND_FRAMEWORK], nameConversionRules, ModificationTracker.NEVER_CHANGED),
      PolySymbolsCompoundQueryResultsCustomizer(customizers),
      PolyContextImpl(context),
      allowResolve
    )
}