package com.intellij.javascript.web.refactoring

import com.intellij.javascript.web.findUsages.PsiSourcedWebSymbolReference
import com.intellij.javascript.web.findUsages.PsiSourcedWebSymbolReference.RenameHandler
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.RefactoringHelper
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.usageView.UsageInfo

class PsiSourcedWebSymbolRefactoringHelper : RefactoringHelper<List<RenameHandler>> {

  override fun prepareOperation(usages: Array<out UsageInfo>): List<RenameHandler> =
    usages.mapNotNull { (it.reference as? PsiSourcedWebSymbolReference)?.createRenameHandler() }

  override fun performOperation(project: Project, operationData: List<RenameHandler>?) {
    if (operationData.isNullOrEmpty()) return
    WriteAction.run<Throwable> {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      RenameUtil.renameNonCodeUsages(project, operationData.mapNotNull { it.handleRename() }.toTypedArray())
    }
  }

}