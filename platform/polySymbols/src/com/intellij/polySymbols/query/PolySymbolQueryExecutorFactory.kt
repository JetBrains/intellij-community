// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.query.impl.PolySymbolQueryExecutorBuilderImpl
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

/**
 * Use the factory to create [PolySymbolQueryExecutor] for a particular location in the source code.
 */
interface PolySymbolQueryExecutorFactory : Disposable {

  fun create(location: PsiElement?, allowResolve: Boolean = true): PolySymbolQueryExecutor

  @TestOnly
  fun addScope(scope: PolySymbolScope, contextDirectory: VirtualFile?, disposable: Disposable)

  interface PolySymbolQueryExecutorBuilder {
    fun addRootScope(scope: PolySymbolScope): PolySymbolQueryExecutorBuilder

    fun addRootScopes(scope: List<PolySymbolScope>): PolySymbolQueryExecutorBuilder

    fun addCustomizer(customizer: PolySymbolQueryResultsCustomizer): PolySymbolQueryExecutorBuilder

    fun addNameConversionRules(rules: PolySymbolNameConversionRules): PolySymbolQueryExecutorBuilder

    fun setFramework(framework: String): PolySymbolQueryExecutorBuilder

    fun addPolyContext(kind: PolyContextKind, name: PolyContextName?): PolySymbolQueryExecutorBuilder

    fun allowResolve(allowResolve: Boolean): PolySymbolQueryExecutorBuilder

    fun create(): PolySymbolQueryExecutor
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): PolySymbolQueryExecutorFactory = project.service()

    fun create(location: PsiElement, allowResolve: Boolean = true): PolySymbolQueryExecutor =
      getInstance(location.project).create(location, allowResolve)

    fun createCustom(): PolySymbolQueryExecutorBuilder =
      PolySymbolQueryExecutorBuilderImpl()

    fun createCustom(setup: PolySymbolQueryExecutorBuilder.() -> Unit): PolySymbolQueryExecutor =
      PolySymbolQueryExecutorBuilderImpl()
        .let { setup(it); it.create() }

  }

}