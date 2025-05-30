// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.query.impl.PolySymbolsQueryExecutorBuilderImpl
import org.jetbrains.annotations.TestOnly

/**
 * Use the factory to create [PolySymbolsQueryExecutor] for a particular location in the source code.
 */
interface PolySymbolsQueryExecutorFactory : Disposable {

  fun create(location: PsiElement?, allowResolve: Boolean = true): PolySymbolsQueryExecutor

  @TestOnly
  fun addScope(scope: PolySymbolsScope, contextDirectory: VirtualFile?, disposable: Disposable)

  interface PolySymbolsQueryExecutorBuilder {
    fun addRootScope(scope: PolySymbolsScope): PolySymbolsQueryExecutorBuilder

    fun addRootScopes(scope: List<PolySymbolsScope>): PolySymbolsQueryExecutorBuilder

    fun addCustomizer(customizer: PolySymbolsQueryResultsCustomizer): PolySymbolsQueryExecutorBuilder

    fun addNameConversionRules(rules: PolySymbolNameConversionRules): PolySymbolsQueryExecutorBuilder

    fun setFramework(framework: String): PolySymbolsQueryExecutorBuilder

    fun addPolyContext(kind: PolyContextKind, name: PolyContextName?): PolySymbolsQueryExecutorBuilder

    fun allowResolve(allowResolve: Boolean): PolySymbolsQueryExecutorBuilder

    fun create(): PolySymbolsQueryExecutor
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): PolySymbolsQueryExecutorFactory = project.service()

    fun create(location: PsiElement, allowResolve: Boolean = true): PolySymbolsQueryExecutor =
      getInstance(location.project).create(location, allowResolve)

    fun createCustom(): PolySymbolsQueryExecutorBuilder =
      PolySymbolsQueryExecutorBuilderImpl()

    fun createCustom(setup: PolySymbolsQueryExecutorBuilder.() -> Unit): PolySymbolsQueryExecutor =
      PolySymbolsQueryExecutorBuilderImpl()
        .let { setup(it); it.create() }

  }

}