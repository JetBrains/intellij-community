// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.polySymbols.search.PsiLinkedPolySymbol
import com.intellij.psi.PsiElement

interface PsiLinkedPolySymbolDelegate<T : PsiLinkedPolySymbol> : PolySymbolDelegate<T>, PsiLinkedPolySymbol {

  override val linkedElement: PsiElement?
    get() = delegate.linkedElement

  override val psiContext: PsiElement?
    get() = delegate.psiContext

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    delegate.getNavigationTargets(project)

  override fun createPointer(): Pointer<out PsiLinkedPolySymbolDelegate<T>>

}