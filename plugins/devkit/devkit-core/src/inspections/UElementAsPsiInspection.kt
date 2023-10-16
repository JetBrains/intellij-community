// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.TypeConversionUtil.isAssignable
import com.intellij.psi.util.TypeConversionUtil.isNullType
import com.intellij.util.SmartList
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val NOT_UELEMENT = -2
private const val NOT_PSI_ELEMENT = -1

private val ALLOWED_REDEFINITION = setOf<String?>(
  UClass::class.java.name,
  UMethod::class.java.name,
  UVariable::class.java.name,
  UClassInitializer::class.java.name
)

@VisibleForTesting
class UElementAsPsiInspection : DevKitUastInspectionBase(UMethod::class.java) {

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    // do not analyze nested methods to avoid warning duplicates
    if (method.getParentOfType<UMethod>() != null) return null

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

  override fun isAllowed(holder: ProblemsHolder): Boolean =
    super.isAllowed(holder) &&
    DevKitInspectionUtil.isClassAvailable(holder, UElement::class.java.canonicalName)

  private class CodeVisitor(private val uElementType: PsiClassType, private val psiElementType: PsiClassType) : AbstractUastVisitor() {

    val reportedElements = SmartList<PsiElement>()

    override fun visitCallExpression(node: UCallExpression): Boolean {
      checkReceiver(node)
      checkArguments(node)
      return false
    }

    private fun checkArguments(node: UCallExpression) {
      for (valueArgument in node.valueArguments) {
        if (getDimIfPsiElementType(node.getParameterForArgument(valueArgument)?.type) ==
          getDimIfUElementType(valueArgument.getExpressionType())
        ) {
          valueArgument.sourcePsiElement?.let { reportedElements.add(it) }
        }
      }
    }

    private fun checkReceiver(node: UCallExpression) {
      if (getDimIfUElementType(node.receiverType) == NOT_UELEMENT) return
      val psiMethod = node.resolve() ?: return
      val containingClass = psiMethod.containingClass ?: return
      if (containingClass.qualifiedName in ALLOWED_REDEFINITION) return
      if ((isPsiElementClass(containingClass) || psiMethod.findSuperMethods().any { isPsiElementClass(it.containingClass) }) &&
          psiMethod.findSuperMethods().none { it.containingClass?.qualifiedName in ALLOWED_REDEFINITION }) {
        node.sourcePsiElement?.let {
          reportedElements.add(it)
        }
        return
      }
      val uMethod = node.resolveToUElementOfType<UMethod>() ?: return
      if (UElementAsPsiCheckProviders.allForLanguage(node.lang).any { it.isPsiElementReceiver(uMethod) }) {
        node.receiver?.sourcePsi?.let {
          reportedElements.add(it)
        }
      }
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (node.operator != UastBinaryOperator.ASSIGN) return false
      if (getDimIfUElementType(node.rightOperand.getExpressionType()) == getDimIfPsiElementType(node.leftOperand.getExpressionType())) {
        node.rightOperand.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false
    }

    override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
      if (getDimIfUElementType(node.operand.getExpressionType()) == getDimIfPsiElementType(node.typeReference?.type)) {
        node.operand.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false
    }

    override fun visitVariable(node: UVariable): Boolean {
      if (getDimIfUElementType(node.uastInitializer?.getExpressionType()) == getDimIfPsiElementType(node.typeReference?.type)) {
        node.uastInitializer.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return false
    }

    override fun visitReturnExpression(node: UReturnExpression): Boolean {
      val expected = when (val jt = node.jumpTarget) {
        is UMethod -> jt.returnType
        is ULambdaExpression -> jt.getExpressionType()
        else -> null
      }
      if (getDimIfUElementType(node.returnExpression?.getExpressionType()) == getDimIfPsiElementType(expected)) {
        node.returnExpression.sourcePsiElement?.let { reportedElements.add(it) }
      }
      return super.visitReturnExpression(node)
    }

    private fun getDimIfPsiElementType(type: PsiType?): Int {
      val dim = type?.arrayDimensions ?: return NOT_PSI_ELEMENT
      return if (
        type.deepComponentType.let { !isNullType(type) && isAssignable(psiElementType, it) && getDimIfUElementType(it) == NOT_UELEMENT }
      ) dim
      else NOT_PSI_ELEMENT
    }

    private fun isPsiElementClass(cls: PsiClass?): Boolean {
      if (cls == null) return false
      val qualifiedName = cls.qualifiedName ?: return false
      return getDimIfPsiElementType(PsiType.getTypeByName(qualifiedName, cls.project, cls.resolveScope)) != NOT_PSI_ELEMENT
    }

    private fun getDimIfUElementType(type: PsiType?): Int {
      val dim = type?.arrayDimensions ?: return NOT_UELEMENT
      return if (type.deepComponentType.let { !isNullType(type) && isAssignable(uElementType, it) }) dim else NOT_UELEMENT
    }
  }

  private fun psiClassType(fqn: String, searchScope: GlobalSearchScope): PsiClassType? {
    val project = searchScope.project ?: return null
    return PsiType.getTypeByName(fqn, project, searchScope).takeIf { it.resolve() != null }
  }

}
