// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.abstraction

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.actions.cache.FilesRecoveryScope
import com.intellij.ide.actions.cache.Saul
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.RescanIndexesAction
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.BaseInspection
import com.siyeh.ig.BaseInspectionVisitor
import com.siyeh.ig.InspectionGadgetsFix

class UnresolvedClassReferenceRepairInspection : BaseInspection() {
  override fun buildErrorString(vararg infos: Any): String {
    val refName = infos.first()
    return InspectionGadgetsBundle.message("unresolved.class.reference.repair.problem.descriptor", refName)
  }

  override fun buildVisitor(): BaseInspectionVisitor = RepairUnresolvedReferenceVisitor()

  override fun buildFix(vararg infos: Any?): InspectionGadgetsFix = RepairUnresolvedReferenceFix()

  private class RepairUnresolvedReferenceFix : InspectionGadgetsFix(), LowPriorityAction {

    override fun getFamilyName() = JavaBundle.message("unresolved.class.reference.repair.message")

    override fun doFix(project: Project, descriptor: ProblemDescriptor) {
      val identifier = descriptor.psiElement
      if (identifier is PsiIdentifier) {
        val name = identifier.text ?: return

        runBackgroundableTask(CodeInsightBundle.message("progress.title.resolving.reference"), project, true) {
          val files = runReadAction {
            FilenameIndex.getVirtualFilesByName("$name.java", GlobalSearchScope.allScope(project))
          }
          service<Saul>().sortThingsOut(FilesRecoveryScope(project, files), listOf(RescanIndexesAction()))
        }
      }
    }

    override fun startInWriteAction() = false
  }
}

private class RepairUnresolvedReferenceVisitor : BaseInspectionVisitor() {
  override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
    super.visitReferenceElement(reference)
    checkReference(reference)
  }

  override fun visitReferenceExpression(expression: PsiReferenceExpression) {
    super.visitReferenceExpression(expression)
    checkReference(expression)
  }

  private fun checkReference(reference: PsiJavaCodeReferenceElement) {
    if (!isAvailable(reference)) return

    val identifier = PsiTreeUtil.getChildOfType(reference, PsiIdentifier::class.java) ?: return
    registerError(identifier, identifier.text)
  }


  private fun isAvailable(reference: PsiJavaCodeReferenceElement): Boolean {
    if (reference.resolve() != null) return false
    var parent = reference.parent

    if (parent is PsiAnnotation ||
        parent is PsiJavaCodeReferenceCodeFragment ||
        parent is PsiReferenceList ||
        parent is PsiAnonymousClass) {
      return true
    }

    if (PsiTreeUtil.getParentOfType(reference, PsiImportStatement::class.java, PsiImportStaticStatement::class.java) != null) return true
    if (isNewExpression(reference)) return true
    if (parent is PsiExpression && parent !is PsiReferenceExpression) return false

    if (parent is PsiTypeElement) {
      if (parent.parent is PsiReferenceParameterList) return true
      while (parent.parent is PsiTypeElement) {
        parent = parent.parent
        if (parent.parent is PsiReferenceParameterList) return true
      }

      if (parent.parent is PsiCodeFragment ||
          parent.parent is PsiVariable ||
          parent.parent is PsiMethod ||
          parent.parent is PsiClassObjectAccessExpression ||
          parent.parent is PsiTypeCastExpression ||
          parent.parent is PsiInstanceOfExpression) {
        return true
      }
    }

    if (reference is PsiReferenceExpression) {
      return parent !is PsiMethodCallExpression
    }
    return false
  }

  private fun isNewExpression(reference: PsiJavaCodeReferenceElement): Boolean {
    val newExpression = PsiTreeUtil.getParentOfType(reference, PsiNewExpression::class.java)
    val expressionList = PsiTreeUtil.getParentOfType(reference, PsiExpressionList::class.java)
    return newExpression != null && reference.parent !is PsiTypeElement &&
           (expressionList == null || !PsiTreeUtil.isAncestor(newExpression, expressionList, false))
  }
}
