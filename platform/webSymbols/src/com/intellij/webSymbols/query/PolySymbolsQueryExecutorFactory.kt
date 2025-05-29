// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.query.impl.WebSymbolsQueryExecutorBuilderImpl
import org.jetbrains.annotations.TestOnly

/**
 * Use the factory to create [PolySymbolsQueryExecutor] for a particular location in the source code.
 */
interface PolySymbolsQueryExecutorFactory : Disposable {

  fun create(location: PsiElement?, allowResolve: Boolean = true): PolySymbolsQueryExecutor

  @TestOnly
  fun addScope(scope: PolySymbolsScope, contextDirectory: VirtualFile?, disposable: Disposable)

  interface WebSymbolsQueryExecutorBuilder {
    fun addRootScope(scope: PolySymbolsScope): WebSymbolsQueryExecutorBuilder

    fun addRootScopes(scope: List<PolySymbolsScope>): WebSymbolsQueryExecutorBuilder

    fun addCustomizer(customizer: PolySymbolsQueryResultsCustomizer): WebSymbolsQueryExecutorBuilder

    fun addNameConversionRules(rules: PolySymbolNameConversionRules): WebSymbolsQueryExecutorBuilder

    fun setFramework(framework: String): WebSymbolsQueryExecutorBuilder

    fun addWebSymbolsContext(kind: ContextKind, name: ContextName?): WebSymbolsQueryExecutorBuilder

    fun allowResolve(allowResolve: Boolean): WebSymbolsQueryExecutorBuilder

    fun create(): PolySymbolsQueryExecutor
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): PolySymbolsQueryExecutorFactory = project.service()

    fun create(location: PsiElement, allowResolve: Boolean = true): PolySymbolsQueryExecutor =
      getInstance(location.project).create(location, allowResolve)

    fun createCustom(): WebSymbolsQueryExecutorBuilder =
      WebSymbolsQueryExecutorBuilderImpl()

    fun createCustom(setup: WebSymbolsQueryExecutorBuilder.() -> Unit): PolySymbolsQueryExecutor =
      WebSymbolsQueryExecutorBuilderImpl()
        .let { setup(it); it.create() }

  }

}