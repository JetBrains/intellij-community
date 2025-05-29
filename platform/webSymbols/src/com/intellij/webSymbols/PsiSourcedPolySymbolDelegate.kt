// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.PsiElement

abstract class PsiSourcedPolySymbolDelegate<T : PsiSourcedPolySymbol>(delegate: T) : PolySymbolDelegate<T>(delegate), PsiSourcedPolySymbol {

  override val source: PsiElement?
    get() = delegate.source

  override val psiContext: PsiElement?
    get() = delegate.psiContext

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    delegate.getNavigationTargets(project)

}