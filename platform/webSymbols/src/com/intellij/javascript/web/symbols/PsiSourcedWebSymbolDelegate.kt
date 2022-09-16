package com.intellij.javascript.web.symbols

import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

abstract class PsiSourcedWebSymbolDelegate<T : PsiSourcedWebSymbol>(delegate: T) : WebSymbolDelegate<T>(delegate), PsiSourcedWebSymbol {

  override val source: PsiElement?
    get() = delegate.source

  override val psiContext: PsiElement?
    get() = delegate.psiContext

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    delegate.getNavigationTargets(project)

}