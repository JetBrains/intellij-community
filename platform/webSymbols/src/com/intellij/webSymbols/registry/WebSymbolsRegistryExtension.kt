// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.WebSymbolsContainer
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContextRulesProvider

/*
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("DEPRECATION")
interface WebSymbolsRegistryExtension {

  fun getContainers(project: Project, element: PsiElement?, context: WebSymbolsContext, allowResolve: Boolean): List<WebSymbolsContainer> =
    emptyList()

  fun getContextRulesProviders(dir: PsiDirectory): List<WebSymbolsContextRulesProvider> =
    emptyList()

  fun getNameConversionRulesProviders(project: Project, element: PsiElement?, context: WebSymbolsContext): List<WebSymbolNameConversionRulesProvider> =
    emptyList()

  fun beforeRegistryCreation(project: Project, element: PsiElement?) {
  }

  companion object {

    internal val EP_NAME = ExtensionPointName<WebSymbolsRegistryExtension>("com.intellij.webSymbols.registryExtension")

  }

}