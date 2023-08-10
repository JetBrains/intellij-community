package com.intellij.webSymbols.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SyntheticElement
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.webSymbols.PsiSourcedWebSymbol

class PsiSourcedWebSymbolRenameHandler : RenameHandler {

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
      ?.filterIsInstance<PsiSourcedWebSymbol>()
      ?.mapNotNull { it.source }
      ?.count { it is PsiNamedElement && it !is SyntheticElement } == 1

}