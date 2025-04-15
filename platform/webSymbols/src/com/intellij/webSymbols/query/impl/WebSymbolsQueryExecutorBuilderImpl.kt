// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.openapi.util.ModificationTracker
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.context.impl.WebSymbolsContextImpl
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory.WebSymbolsQueryExecutorBuilder
import com.intellij.webSymbols.query.WebSymbolsQueryResultsCustomizer

class WebSymbolsQueryExecutorBuilderImpl() : WebSymbolsQueryExecutorBuilder {
  private val rootScopes = mutableListOf<WebSymbolsScope>()
  private val customizers = mutableListOf<WebSymbolsQueryResultsCustomizer>()
  private val nameConversionRules = mutableListOf<WebSymbolNameConversionRules>()
  private val context = mutableMapOf<ContextKind, ContextName>()
  private var allowResolve = true

  override fun addRootScope(scope: WebSymbolsScope): WebSymbolsQueryExecutorBuilder = apply {
    rootScopes.add(scope)
  }

  override fun addRootScopes(scope: List<WebSymbolsScope>): WebSymbolsQueryExecutorBuilder = apply {
    rootScopes.addAll(scope)
  }

  override fun addCustomizer(customizer: WebSymbolsQueryResultsCustomizer): WebSymbolsQueryExecutorBuilder = apply {
    customizers.add(customizer)
  }

  override fun addNameConversionRules(rules: WebSymbolNameConversionRules): WebSymbolsQueryExecutorBuilder = apply {
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

  override fun create(): WebSymbolsQueryExecutor =
    WebSymbolsQueryExecutorImpl(
      null,
      rootScopes,
      WebSymbolNamesProviderImpl(context[KIND_FRAMEWORK], nameConversionRules, ModificationTracker.NEVER_CHANGED),
      WebSymbolsCompoundQueryResultsCustomizer(customizers),
      WebSymbolsContextImpl(context),
      allowResolve
    )
}