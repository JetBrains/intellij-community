// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

interface WebSymbolsRegistryManager : Disposable {

  fun get(location: PsiElement?, allowResolve: Boolean = true): WebSymbolsRegistry

  @TestOnly
  fun addSymbolsContainer(container: WebSymbolsContainer, contextDirectory: VirtualFile?, disposable: Disposable)

  companion object {

    @JvmStatic
    fun getInstance(project: Project): WebSymbolsRegistryManager = project.service()

    fun get(contextElement: PsiElement, allowResolve: Boolean = true): WebSymbolsRegistry =
      contextElement.project.service<WebSymbolsRegistryManager>().get(contextElement, allowResolve)

  }

}