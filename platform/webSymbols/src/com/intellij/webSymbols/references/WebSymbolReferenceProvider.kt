// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.startOffsetInAncestor
import com.intellij.webSymbols.WebSymbol
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Extend WebSymbolPsiReferenceProvider instead and register with com.intellij.webSymbols.psiReferenceProvider extension point")
abstract class WebSymbolReferenceProvider<T : PsiExternalReferenceHost> : PsiSymbolReferenceProvider {

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> =
    CachedValuesManager.getCachedValue(element, CachedValuesManager.getManager(element.project).getKeyForClass(this.javaClass)) {
      @Suppress("UNCHECKED_CAST")
      (CachedValueProvider.Result.create(getReferences(element as T),
                                         PsiModificationTracker.MODIFICATION_COUNT))
    }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()

  protected open fun getSymbol(psiElement: T): WebSymbol? = null

  protected open fun getSymbolNameOffset(psiElement: T): Int = 0

  protected open fun getOffsetsToSymbols(psiElement: T): Map<Int, WebSymbol> =
    getSymbol(psiElement)
      ?.let { mapOf(getSymbolNameOffset(psiElement) to it) }
    ?: emptyMap()

  protected open fun shouldShowProblems(element: T): Boolean = true

  private fun getReferences(element: T): List<PsiSymbolReference> {
    val showProblems = shouldShowProblems(element)
    return getOffsetsToSymbols(element).flatMap { (offset, symbol) ->
      com.intellij.webSymbols.references.impl.getReferences(element, offset, symbol, showProblems)
    }
  }
  companion object {
    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @Deprecated(message = "Use com.intellij.psi.util.startOffsetInAncestor() instead",
                replaceWith = ReplaceWith("startOffsetInAncestor(parent)"))
    fun PsiElement.startOffsetIn(parent: PsiElement): Int =
      startOffsetInAncestor(parent)
  }
}