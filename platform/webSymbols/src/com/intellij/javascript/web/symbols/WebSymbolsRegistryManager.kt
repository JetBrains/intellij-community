package com.intellij.javascript.web.symbols

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
interface WebSymbolsRegistryManager: Disposable {

  fun get(contextElement: PsiElement?, allowResolve: Boolean = true): WebSymbolsRegistry

  @TestOnly
  fun addSymbolsContainer(container: WebSymbolsContainer, contextDirectory: VirtualFile?, disposable: Disposable)

  companion object {

    @JvmStatic
    fun getInstance(project: Project): WebSymbolsRegistryManager = project.service()

    fun get(contextElement: PsiElement, allowResolve: Boolean = true): WebSymbolsRegistry =
      contextElement.project.service<WebSymbolsRegistryManager>().get(contextElement, allowResolve)

  }

}