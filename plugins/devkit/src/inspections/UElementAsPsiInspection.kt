/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.TypeConversionUtil.isAssignable
import com.intellij.psi.util.TypeConversionUtil.isNullType
import com.intellij.util.SmartList
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class UElementAsPsiInspection : DevKitUastInspectionBase() {

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val sourcePsiElement = method.sourcePsiElement ?: return null
    val uElementType = psiClassType(UElement::class.java.name, sourcePsiElement.resolveScope) ?: return null
    val psiElementType = psiClassType(PsiElement::class.java.name, sourcePsiElement.resolveScope) ?: return null
    val visitor = CodeVisitor(uElementType, psiElementType)
    method.accept(visitor)
    return visitor.reportedElements.map {
      manager.createProblemDescriptor(
        it,
        DevKitBundle.message("inspections.usage.uelement.as.psi"),
        emptyArray<LocalQuickFix>(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
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
      for (valueArgument in node.valueArguments) {
        if (!isUElementType(valueArgument.getExpressionType())) continue
        val parameter = guessCorrespondingParameter(node, valueArgument) ?: continue
        if (isPsiElementType(parameter.type)) {
          valueArgument.sourcePsiElement?.let { reportedElements.add(it) }
        }
      }
    }

    /**
     * A workaround for IDEA-184046
     * tries to find parameter in declaration that corresponds to an argument:
     * considers simple positional calls and Kotlin extension calls.
     */
    private fun guessCorrespondingParameter(callExpression: UCallExpression, arg: UExpression): PsiParameter? {
      val psiMethod = callExpression.resolve() ?: return null
      val parameters = psiMethod.parameterList.parameters

      if (callExpression is UCallExpressionEx)
        return parameters.withIndex().find { (i, _) -> callExpression.getArgumentForParameter(i) == arg }?.value

      // not everyone implements UCallExpressionEx, lets try to guess
      val indexInArguments = callExpression.valueArguments.indexOf(arg)
      if (parameters.size == callExpression.valueArguments.count()) {
        return parameters.getOrNull(indexInArguments)
      }
      // probably it is a kotlin extension method
      if (parameters.size - 1 == callExpression.valueArguments.count()) {
        val parameter = parameters.firstOrNull() ?: return null
        val receiverType = callExpression.receiverType ?: return null
        if (!parameter.type.isAssignableFrom(receiverType)) return null
        if (!parameters.drop(1).zip(callExpression.valueArguments)
          .all { (param, arg) -> arg.getExpressionType()?.let { param.type.isAssignableFrom(it) } == true }) return null
        return parameters.getOrNull(indexInArguments + 1)
      }
      //named parameters are not processed
      return null
    }


    private fun checkReceiver(node: UCallExpression) {
      if (!isUElementType(node.receiverType)) return
      val psiMethod = node.resolve() ?: return
      val containingClass = psiMethod.containingClass ?: return
      if (containingClass.qualifiedName in ALLOWED_REDEFINITION) return
      if (!isPsiElementClass(containingClass) && psiMethod.findSuperMethods().none { isPsiElementClass(it.containingClass) }) return
      if (psiMethod.findSuperMethods().any { it.containingClass?.qualifiedName in ALLOWED_REDEFINITION }) return
      node.sourcePsiElement?.let {
        reportedElements.add(it)
      }
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (node.operator != UastBinaryOperator.ASSIGN) return false
      if (isUElementType(node.rightOperand.getExpressionType()) && isPsiElementType(node.leftOperand.getExpressionType())) {
        node.rightOperand.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false;
    }

    override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
      if (isUElementType(node.operand.getExpressionType()) && isPsiElementType(node.typeReference?.type)) {
        node.operand.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false
    }

    override fun visitVariable(node: UVariable): Boolean {
      if (isUElementType(node.uastInitializer?.getExpressionType()) && isPsiElementType(node.type)) {
        node.uastInitializer.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false
    }

    private fun isPsiElementType(type: PsiType?) =
      type?.let { !isNullType(type) && isAssignable(psiElementType, it) && !isUElementType(it) } ?: false

    private fun isPsiElementClass(cls: PsiClass?): Boolean {
      if (cls == null) return false
      return isPsiElementType(PsiType.getTypeByName(cls.qualifiedName, cls.project, cls.resolveScope))
    }

    private fun isUElementType(type: PsiType?) =
      type?.let { !isNullType(type) && isAssignable(uElementType, it) } ?: false
  }

  private fun psiClassType(fqn: String, searchScope: GlobalSearchScope): PsiClassType? =
    PsiType.getTypeByName(fqn, searchScope.project, searchScope).takeIf { it.resolve() != null }

  private companion object {
    val ALLOWED_REDEFINITION = setOf(
      UClass::class.java.name,
      UMethod::class.java.name,
      UVariable::class.java.name,
      UClassInitializer::class.java.name
    )
  }

}