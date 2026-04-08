// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeModuleKindResolver.doesApiKindMatchExpectedModuleKind
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

@VisibleForTesting
@ApiStatus.Internal
class SplitModeApiUsageInspection : DevKitUastInspectionBase(UClass::class.java, UField::class.java, UMethod::class.java) {

  private val restrictionsService = SplitModeApiRestrictionsService.getInstance()

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    if (!super.isAllowed(holder)) return false

    val isRestrictionServiceReady = restrictionsService.isLoaded()
    if (isRestrictionServiceReady) {
      return true
    }
    else {
      restrictionsService.scheduleLoadRestrictions()
      return false
    }
  }

  override fun checkClass(
    aClass: UClass,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<out ProblemDescriptor?>? {
    val sourcePsi = aClass.sourcePsi ?: return null
    val moduleType = SplitModeModuleKindResolver.getOrComputeModuleKind(sourcePsi)
    val descriptors = SmartList<ProblemDescriptor>()
    aClass.uastSuperTypes.forEach { superTypeExpression ->
      checkApiUsage(superTypeExpression, moduleType, manager, isOnTheFly, descriptors)
    }

    return if (descriptors.isEmpty()) null else descriptors.toTypedArray()
  }

  override fun checkMethod(
    method: @NotNull UMethod,
    manager: @NotNull InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    return checkBody(method, manager, isOnTheFly)
  }

  private fun checkBody(
    uElement: @NotNull UElement,
    manager: @NotNull InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    val sourcePsi = uElement.sourcePsi ?: return null
    val moduleType = SplitModeModuleKindResolver.getOrComputeModuleKind(sourcePsi)
    val descriptors = SmartList<ProblemDescriptor>()

    uElement.accept(object : AbstractUastVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        checkApiUsage(node, moduleType, manager, isOnTheFly, descriptors)
        return true
      }

      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
        // For a.b.c.d, check left-to-right and stop at the first error
        val sizeBeforeCheck = descriptors.size
        checkApiUsage(node, moduleType, manager, isOnTheFly, descriptors)
        val errorReported = descriptors.size > sizeBeforeCheck

        // If error reported, skip visiting children
        return errorReported
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
        checkApiUsage(node, moduleType, manager, isOnTheFly, descriptors)
        return true
      }
    })

    return if (descriptors.isEmpty()) null else descriptors.toTypedArray()
  }

  private fun checkApiUsage(
    expression: UExpression,
    currentModuleType: SplitModeApiRestrictionsService.ModuleKind,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    descriptors: MutableList<ProblemDescriptor>,
  ) {
    val resolvedApi = resolveApiUsage(expression) ?: return
    val expectedModuleKind = restrictionsService.getCodeApiKind(resolvedApi.qualifiedName, resolvedApi.owner) ?: return

    if (!doesApiKindMatchExpectedModuleKind(currentModuleType, expectedModuleKind)) {
      val sourcePsi = expression.sourcePsi ?: return
      val message = DevKitBundle.message(
        "inspection.api.usage.restricted.to.module.type.default.message",
        resolvedApi.qualifiedName,
        expectedModuleKind.presentableName
      )

      descriptors.add(
        manager.createProblemDescriptor(
          sourcePsi,
          message,
          isOnTheFly,
          emptyArray<LocalQuickFix>(),
          ProblemHighlightType.WEAK_WARNING
        )
      )
    }
  }

  private fun resolveApiUsage(expression: UExpression): ResolvedApiUsage? {
    val qualifiedName = getResolvedFqn(expression) ?: return null
    return ResolvedApiUsage(qualifiedName, getResolvedApiOwner(expression))
  }

  private fun getResolvedApiOwner(expression: UExpression): PsiModifierListOwner? {
    return when (expression) {
      is UCallExpression -> {
        val resolved = expression.resolve()
        resolved as? PsiModifierListOwner ?: PsiTypesUtil.getPsiClass(expression.returnType)
      }

      is UQualifiedReferenceExpression -> {
        val resolved = expression.resolve()
        resolved as? PsiModifierListOwner ?: PsiTypesUtil.getPsiClass(expression.getExpressionType())
      }

      is USimpleNameReferenceExpression -> expression.resolve() as? PsiModifierListOwner
      is UTypeReferenceExpression -> PsiTypesUtil.getPsiClass(expression.type)
      else -> null
    }
  }

  private fun getResolvedFqn(expression: UExpression): String? {
    return when (expression) {
      is UCallExpression -> {
        if (expression.kind == UastCallKind.CONSTRUCTOR_CALL) {
          PsiTypesUtil.getPsiClass(expression.returnType)?.qualifiedName
        }
        else {
          expression.resolve()?.let { resolved ->
            when {
              resolved is PsiClass -> resolved.qualifiedName
              else -> PsiTypesUtil.getPsiClass((resolved as? UMethod)?.returnType)?.qualifiedName
            }
          }
        }
      }

      is UQualifiedReferenceExpression -> {
        val resolved = expression.resolve()
        when (resolved) {
          is PsiClass -> {
            resolved.qualifiedName
          }

          is PsiMethod -> {
            val uMethod = resolved.toUElementOfType<UMethod>() ?: return null
            val containingClass = uMethod.getContainingUClass() ?: return null
            "${containingClass.qualifiedName}.${uMethod.name}"
          }

          null -> {
            null
          }

          else -> {
            PsiTypesUtil.getPsiClass(expression.getExpressionType())?.qualifiedName
          }
        }
      }

      is USimpleNameReferenceExpression -> {
        val resolved = expression.resolve()
        when (resolved) {
          is PsiClass -> resolved.qualifiedName
          else -> null
        }
      }

      is UTypeReferenceExpression -> {
        expression.getQualifiedName()
      }

      else -> null
    }
  }

  private data class ResolvedApiUsage(
    val qualifiedName: String,
    val owner: PsiModifierListOwner?,
  )
}
