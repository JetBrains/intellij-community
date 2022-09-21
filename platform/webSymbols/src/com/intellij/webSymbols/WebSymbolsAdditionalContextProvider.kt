// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.framework.WebFrameworksConfiguration

@Suppress("DEPRECATION")
interface WebSymbolsAdditionalContextProvider {

  @JvmDefault
  fun getAdditionalContext(project: Project, element: PsiElement?, framework: String?, allowResolve: Boolean): List<WebSymbolsContainer> =
    emptyList()

  @JvmDefault
  fun getFrameworkConfigurations(dir: PsiDirectory): Pair<List<WebFrameworksConfiguration>, ModificationTracker> =
    Pair(emptyList(), ModificationTracker.NEVER_CHANGED)

  @JvmDefault
  fun beforeRegistryCreation(project: Project, element: PsiElement?) {
  }

  companion object {

    internal val EP_NAME = ExtensionPointName<WebSymbolsAdditionalContextProvider>("com.intellij.webSymbols.additionalContextProvider")

  }

}