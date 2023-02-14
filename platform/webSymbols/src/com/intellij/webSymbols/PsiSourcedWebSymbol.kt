// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

interface PsiSourcedWebSymbol : WebSymbol {

  override val psiContext: PsiElement?
    get() = source

  val source: PsiElement?
    get() = null

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    source?.let { listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(it)) } ?: emptyList()

  override fun isEquivalentTo(symbol: Symbol): Boolean {
    if (this == symbol) return true
    val source = this.source ?: return false
    val target = PsiSymbolService.getInstance().extractElementFromSymbol(symbol) ?: return false
    return target.manager.areElementsEquivalent(source, target)
  }

}