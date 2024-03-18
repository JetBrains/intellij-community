package com.intellij.webSymbols.refactoring

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringHelper
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.usageView.UsageInfo
import com.intellij.webSymbols.search.PsiSourcedWebSymbolReference
import com.intellij.webSymbols.search.PsiSourcedWebSymbolReference.RenameHandler

class PsiSourcedWebSymbolRefactoringHelper : RefactoringHelper<List<RenameHandler>> {
  override fun prepareOperation(usages: Array<out UsageInfo>, elements: List<PsiElement>): List<RenameHandler> =
    usages.mapNotNull { (it.reference as? PsiSourcedWebSymbolReference)?.createRenameHandler() }

  override fun performOperation(project: Project, operationData: List<RenameHandler>?) {
    if (operationData.isNullOrEmpty()) return
    WriteAction.run<Throwable> {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      RenameUtil.renameNonCodeUsages(project, operationData.mapNotNull { it.handleRename() }.toTypedArray())
    }
  }

}