// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.context.PolyContext
import com.intellij.webSymbols.context.PolyContextRulesProvider
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

interface WebSymbolsQueryConfigurator {

  fun getScope(project: Project, location: PsiElement?, context: PolyContext, allowResolve: Boolean): List<PolySymbolsScope> =
    emptyList()

  fun getContextRulesProviders(project: Project, dir: VirtualFile): List<PolyContextRulesProvider> =
    emptyList()

  fun getNameConversionRulesProviders(project: Project,
                                      element: PsiElement?,
                                      context: PolyContext): List<WebSymbolNameConversionRulesProvider> =
    emptyList()

  fun beforeQueryExecutorCreation(project: Project) {
  }

  companion object {

    @TestOnly
    @Internal
    val EP_NAME: ExtensionPointName<WebSymbolsQueryConfigurator> = ExtensionPointName<WebSymbolsQueryConfigurator>("com.intellij.webSymbols.queryConfigurator")

  }

}