/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.TypeConversionUtil.isAssignable
import com.intellij.util.SmartList
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class UElementAsPsiInspection : DevKitUastInspectionBase() {

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val sourcePsiElement = method.sourcePsiElement ?: return null
    val module = ModuleUtilCore.findModuleForPsiElement(sourcePsiElement) ?: return null
    val uElementType = psiClassType(module, UELEMENT_FQN) ?: return null
    val psiElementType = psiClassType(module, PSI_ELEMENT_FQN) ?: return null
    val visitor = CodeVisitor(uElementType, psiElementType)
    method.accept(visitor)
    return visitor.reportedElements.map {
      manager.createProblemDescriptor(
        it,
        DevKitBundle.message("usage.uelement.as.psi"),
        emptyArray<LocalQuickFix>(), ProblemHighlightType.LIKE_DEPRECATED,
        isOnTheFly,
        false)
    }.toTypedArray()
  }

  private class CodeVisitor(private val uElementType: PsiClassType, private val psiElementType: PsiClassType) : AbstractUastVisitor() {

    val reportedElements = SmartList<PsiElement>()

    override fun visitCallExpression(node: UCallExpression): Boolean {
      checkReceiver(node)
      checkArguments(node)
      return false;
    }

    private fun checkArguments(node: UCallExpression) {
      for ((i, valueArgument) in node.valueArguments.withIndex()) {
        if (isUElementType(valueArgument.getExpressionType()) &&
            isPsiElementType(node.resolve()?.parameterList?.parameters?.getOrNull(i)?.type)) {
          valueArgument.sourcePsiElement?.let { reportedElements.add(it) }
        }
      }
    }

    private fun checkReceiver(node: UCallExpression) {
      if (!isUElementType(node.receiverType)) return
      val psiMethod = node.resolve() ?: return
      if (isPsiElementClass(psiMethod.containingClass) || psiMethod.findSuperMethods().any { isPsiElementClass(it.containingClass) }) {
        node.sourcePsiElement?.let { reportedElements.add(it) }
      }
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (node.operator != UastBinaryOperator.ASSIGN) return false
      if (isUElementType(node.rightOperand.getExpressionType()) && isPsiElementType(node.leftOperand.getExpressionType())) {
        node.rightOperand.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false;
    }

    override fun visitVariable(node: UVariable): Boolean {
      if (isUElementType(node.uastInitializer?.getExpressionType()) && isPsiElementType(node.type)) {
        node.uastInitializer.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false
    }

    private fun isPsiElementType(type: PsiType?) =
      type?.let { isAssignable(psiElementType, it) && !isUElementType(it) } ?: false

    private fun isPsiElementClass(cls: PsiClass?): Boolean {
      if (cls == null) return false
      return isPsiElementType(PsiType.getTypeByName(cls.qualifiedName, cls.project, GlobalSearchScope.allScope(cls.project)))
    }

    private fun isUElementType(type: PsiType?) =
      type?.let { isAssignable(uElementType, it) } ?: false
  }

  private fun psiClassType(module: Module, fqn: String): PsiClassType? =
    PsiType.getTypeByName(fqn, module.project, GlobalSearchScope.moduleWithLibrariesScope(module)).takeIf { it.resolve() != null }

  companion object {
    private const val UELEMENT_FQN = "org.jetbrains.uast.UElement"

    private const val PSI_ELEMENT_FQN = "com.intellij.psi.PsiElement"
  }

}