// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.query.impl.WebSymbolsQueryExecutorBuilderImpl
import org.jetbrains.annotations.TestOnly

/**
 * Use the factory to create [WebSymbolsQueryExecutor] for a particular location in the source code.
 */
interface WebSymbolsQueryExecutorFactory : Disposable {

  fun create(location: PsiElement?, allowResolve: Boolean = true): WebSymbolsQueryExecutor

  @TestOnly
  fun addScope(scope: WebSymbolsScope, contextDirectory: VirtualFile?, disposable: Disposable)

  interface WebSymbolsQueryExecutorBuilder {
    fun addRootScope(scope: WebSymbolsScope): WebSymbolsQueryExecutorBuilder

    fun addRootScopes(scope: List<WebSymbolsScope>): WebSymbolsQueryExecutorBuilder

    fun addCustomizer(customizer: WebSymbolsQueryResultsCustomizer): WebSymbolsQueryExecutorBuilder

    fun addNameConversionRules(rules: WebSymbolNameConversionRules): WebSymbolsQueryExecutorBuilder

    fun setFramework(framework: String): WebSymbolsQueryExecutorBuilder

    fun addWebSymbolsContext(kind: ContextKind, name: ContextName?): WebSymbolsQueryExecutorBuilder

    fun allowResolve(allowResolve: Boolean): WebSymbolsQueryExecutorBuilder

    fun create(): WebSymbolsQueryExecutor
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): WebSymbolsQueryExecutorFactory = project.service()

    fun create(location: PsiElement, allowResolve: Boolean = true): WebSymbolsQueryExecutor =
      getInstance(location.project).create(location, allowResolve)

    fun createCustom(): WebSymbolsQueryExecutorBuilder =
      WebSymbolsQueryExecutorBuilderImpl()

    fun createCustom(setup: WebSymbolsQueryExecutorBuilder.() -> Unit): WebSymbolsQueryExecutor =
      WebSymbolsQueryExecutorBuilderImpl()
        .let { setup(it); it.create() }

  }

}