// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContextRulesProvider
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

interface PolySymbolQueryConfigurator {

  fun getScope(project: Project, location: PsiElement?, context: PolyContext, allowResolve: Boolean): List<PolySymbolScope> =
    emptyList()

  fun getContextRulesProviders(project: Project, dir: VirtualFile): List<PolyContextRulesProvider> =
    emptyList()

  fun getNameConversionRulesProviders(
    project: Project,
    element: PsiElement?,
    context: PolyContext,
  ): List<PolySymbolNameConversionRulesProvider> =
    emptyList()

  fun beforeQueryExecutorCreation(project: Project) {
  }

  companion object {

    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolQueryConfigurator> = ExtensionPointName<PolySymbolQueryConfigurator>("com.intellij.polySymbols.queryConfigurator")

  }

}