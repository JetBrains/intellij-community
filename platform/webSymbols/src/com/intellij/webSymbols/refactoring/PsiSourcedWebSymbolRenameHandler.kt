// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.refactoring

import com.intellij.ide.TitledHandler
import com.intellij.model.Symbol
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SyntheticElement
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.utils.WebSymbolDeclaredInPsi

private class PsiSourcedWebSymbolRenameHandler : RenameHandler, TitledHandler {

  private var symbol: PsiSourcedWebSymbol? = null

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    if (editor == null || file == null || dataContext == null) return
    val target = dataContext.getData(CommonDataKeys.SYMBOLS)
                   ?.filterIsInstance<PsiSourcedWebSymbol>()
                   ?.map { it.source }
                   ?.singleOrNull() ?: return

    PsiElementRenameHandler.rename(target, target.project, target, editor)
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
  }

  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean =
    dataContext.getData(CommonDataKeys.SYMBOLS)
      ?.singleOrNull { acceptSymbolForPsiSourcedWebSymbolRenameHandler(it) }
      ?.also {
        symbol = it as PsiSourcedWebSymbol
      } != null

  override fun getActionTitle(): @NlsActions.ActionText String? =
    symbol?.presentation?.presentableText

}

internal fun acceptSymbolForPsiSourcedWebSymbolRenameHandler(symbol: Symbol): Boolean =
  symbol is PsiSourcedWebSymbol
  && symbol.source is PsiNamedElement
  && symbol.source !is SyntheticElement