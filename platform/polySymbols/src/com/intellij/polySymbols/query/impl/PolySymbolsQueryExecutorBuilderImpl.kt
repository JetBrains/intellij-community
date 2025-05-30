// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.context.impl.PolyContextImpl
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.query.PolySymbolsQueryExecutorFactory.PolySymbolsQueryExecutorBuilder
import com.intellij.polySymbols.query.PolySymbolsQueryResultsCustomizer

class PolySymbolsQueryExecutorBuilderImpl() : PolySymbolsQueryExecutorBuilder {
  private val rootScopes = mutableListOf<PolySymbolsScope>()
  private val customizers = mutableListOf<PolySymbolsQueryResultsCustomizer>()
  private val nameConversionRules = mutableListOf<PolySymbolNameConversionRules>()
  private val context = mutableMapOf<PolyContextKind, PolyContextName>()
  private var allowResolve = true

  override fun addRootScope(scope: PolySymbolsScope): PolySymbolsQueryExecutorBuilder = apply {
    rootScopes.add(scope)
  }

  override fun addRootScopes(scope: List<PolySymbolsScope>): PolySymbolsQueryExecutorBuilder = apply {
    rootScopes.addAll(scope)
  }

  override fun addCustomizer(customizer: PolySymbolsQueryResultsCustomizer): PolySymbolsQueryExecutorBuilder = apply {
    customizers.add(customizer)
  }

  override fun addNameConversionRules(rules: PolySymbolNameConversionRules): PolySymbolsQueryExecutorBuilder = apply {
    nameConversionRules.add(rules)
  }

  override fun addPolyContext(kind: PolyContextKind, name: PolyContextName?): PolySymbolsQueryExecutorBuilder = apply {
    if (name == null)
      context.remove(kind)
    else
      context[kind] = name
  }

  override fun setFramework(framework: String): PolySymbolsQueryExecutorBuilder = apply {
    context[KIND_FRAMEWORK] = framework
  }

  override fun allowResolve(allowResolve: Boolean): PolySymbolsQueryExecutorBuilder = apply {
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