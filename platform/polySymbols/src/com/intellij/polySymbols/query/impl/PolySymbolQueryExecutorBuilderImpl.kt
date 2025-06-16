// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.context.impl.PolyContextImpl
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory.PolySymbolQueryExecutorBuilder
import com.intellij.polySymbols.query.PolySymbolQueryResultsCustomizer
import com.intellij.polySymbols.query.PolySymbolScope

class PolySymbolQueryExecutorBuilderImpl() : PolySymbolQueryExecutorBuilder {
  private val rootScopes = mutableListOf<PolySymbolScope>()
  private val customizers = mutableListOf<PolySymbolQueryResultsCustomizer>()
  private val nameConversionRules = mutableListOf<PolySymbolNameConversionRules>()
  private val context = mutableMapOf<PolyContextKind, PolyContextName>()
  private var allowResolve = true

  override fun addRootScope(scope: PolySymbolScope): PolySymbolQueryExecutorBuilder = apply {
    rootScopes.add(scope)
  }

  override fun addRootScopes(scope: List<PolySymbolScope>): PolySymbolQueryExecutorBuilder = apply {
    rootScopes.addAll(scope)
  }

  override fun addCustomizer(customizer: PolySymbolQueryResultsCustomizer): PolySymbolQueryExecutorBuilder = apply {
    customizers.add(customizer)
  }

  override fun addNameConversionRules(rules: PolySymbolNameConversionRules): PolySymbolQueryExecutorBuilder = apply {
    nameConversionRules.add(rules)
  }

  override fun addPolyContext(kind: PolyContextKind, name: PolyContextName?): PolySymbolQueryExecutorBuilder = apply {
    if (name == null)
      context.remove(kind)
    else
      context[kind] = name
  }

  override fun setFramework(framework: String): PolySymbolQueryExecutorBuilder = apply {
    context[KIND_FRAMEWORK] = framework
  }

  override fun allowResolve(allowResolve: Boolean): PolySymbolQueryExecutorBuilder = apply {
    this.allowResolve = allowResolve
  }

  override fun create(): PolySymbolQueryExecutor =
    PolySymbolQueryExecutorImpl(
      null,
      rootScopes,
      PolySymbolNamesProviderImpl(context[KIND_FRAMEWORK], nameConversionRules, ModificationTracker.NEVER_CHANGED),
      PolySymbolCompoundQueryResultsCustomizer(customizers),
      PolyContextImpl(context),
      allowResolve
    )
}