// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.buildModuleKindMismatchMessage
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeInspectionUtil.buildModuleKindMismatchTooltipMessage
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver.isApiUsageAllowed
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.ModuleAnalysis
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeQodanaInspectionScopeLimiter
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

@VisibleForTesting
@ApiStatus.Internal
class SplitModeApiUsageInspection : DevKitUastInspectionBase(UClass::class.java, UMethod::class.java) {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!isAllowed(holder)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }

    val logRun = SplitModeInspectionLogging.createRun(SPLIT_MODE_API_USAGE_SHORT_NAME, holder.file)
    if (logRun != null) {
      session.putUserData(LOG_RUN_KEY, logRun)
    }

    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitClass(node: UClass): Boolean {
          addDescriptors(checkClass(node, holder.manager, isOnTheFly, logRun))
          return true
        }

        override fun visitMethod(node: UMethod): Boolean {
          addDescriptors(checkMethod(node, holder.manager, isOnTheFly, logRun))
          return true
        }

        override fun visitField(node: UField): Boolean {
          addDescriptors(checkField(node, holder.manager, isOnTheFly))
          return true
        }

        private fun addDescriptors(descriptors: Array<out ProblemDescriptor?>?) {
          descriptors?.forEach { descriptor ->
            if (descriptor != null) {
              holder.registerProblem(descriptor)
            }
          }
        }
      },
      arrayOf(UClass::class.java, UField::class.java, UMethod::class.java),
    )
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
    session.getUserData(LOG_RUN_KEY)?.logSummary()
  }

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return super.isAllowed(holder)
           && SplitModeInspectionUtil.isAllowedForSplitModeInspection(holder.file)
           && SplitModeQodanaInspectionScopeLimiter.getInstance(holder.file.project).shouldInspectFileInQodanaMode(holder.file)
  }

  override fun checkClass(
    aClass: UClass,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<out ProblemDescriptor?>? {
    return checkClass(aClass, manager, isOnTheFly, null)
  }

  private fun checkClass(
    aClass: UClass,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    logRun: SplitModeInspectionLogRun?,
  ): Array<out ProblemDescriptor?>? {
    val sourcePsi = aClass.sourcePsi ?: return null
    logRun?.recordVisited(sourcePsi)
    val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return null
    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module)
    val descriptors = SmartList<ProblemDescriptor>()
    aClass.uastSuperTypes.forEach { superTypeExpression ->
      checkApiUsage(superTypeExpression, moduleAnalysis, manager, isOnTheFly, descriptors, logRun)
    }

    return if (descriptors.isEmpty()) null else descriptors.toTypedArray()
  }

  override fun checkMethod(
    method: @NotNull UMethod,
    manager: @NotNull InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    return checkMethod(method, manager, isOnTheFly, null)
  }

  private fun checkMethod(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    logRun: SplitModeInspectionLogRun?,
  ): Array<ProblemDescriptor>? {
    return checkBody(method, manager, isOnTheFly, logRun)
  }

  private fun checkBody(
    uElement: @NotNull UElement,
    manager: @NotNull InspectionManager,
    isOnTheFly: Boolean,
    logRun: SplitModeInspectionLogRun?,
  ): Array<ProblemDescriptor>? {
    val sourcePsi = uElement.sourcePsi ?: return null
    logRun?.recordVisited(sourcePsi)
    val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return null
    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module)
    val descriptors = SmartList<ProblemDescriptor>()

    uElement.accept(object : AbstractUastVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        checkApiUsage(node, moduleAnalysis, manager, isOnTheFly, descriptors, logRun)
        return true
      }

      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
        // For a.b.c.d, check left-to-right and stop at the first error
        val sizeBeforeCheck = descriptors.size
        checkApiUsage(node, moduleAnalysis, manager, isOnTheFly, descriptors, logRun)
        val errorReported = descriptors.size > sizeBeforeCheck

        // If error reported, skip visiting children
        return errorReported
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
        checkApiUsage(node, moduleAnalysis, manager, isOnTheFly, descriptors, logRun)
        return true
      }
    })

    return if (descriptors.isEmpty()) null else descriptors.toTypedArray()
  }

  private fun checkApiUsage(
    expression: UExpression,
    currentModuleAnalysis: ModuleAnalysis,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    descriptors: MutableList<ProblemDescriptor>,
    logRun: SplitModeInspectionLogRun?,
  ) {
    // sourcePsi is resolved before resolveApiUsage so that every visited element is recorded,
    // including the ones resolveApiUsage bails out on
    val sourcePsi = expression.sourcePsi ?: return
    logRun?.recordVisited(sourcePsi)
    val resolvedApi = resolveApiUsage(expression) ?: return
    val restrictionsService = SplitModeApiRestrictionsService.getInstance(sourcePsi.project)
    val apiRestriction = restrictionsService.getCodeApiRestriction(resolvedApi.qualifiedName, resolvedApi.owner) ?: return
    val expectedModuleKind = apiRestriction.targetModuleKind
    val currentModuleType = currentModuleAnalysis.resolvedModuleKind

    if (!isApiUsageAllowed(currentModuleAnalysis, apiRestriction)) {
      val hint = restrictionsService.getCodeApiHint(resolvedApi.qualifiedName)
      val message = buildModuleKindMismatchMessage(resolvedApi.qualifiedName, expectedModuleKind, currentModuleType, hint)
      val tooltipMessage = buildModuleKindMismatchTooltipMessage(resolvedApi.qualifiedName, expectedModuleKind, currentModuleType, hint)
      val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return
      val regularFixes = SplitModeDependencyQuickFixes.createMismatchFixes(module, null, expectedModuleKind)
      val suppressionFixes = SplitModeInspectionExclusionsService.getInstance(sourcePsi.project).createCommonSuppressionQuickFixes()
      val fixes = regularFixes + suppressionFixes

      logRun?.recordProblem(sourcePsi)
      descriptors.add(
        manager.createProblemDescriptor(
          sourcePsi,
          null,
          message,
          ProblemHighlightType.WARNING,
          tooltipMessage,
          isOnTheFly,
          *fixes,
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
          val resolved = expression.resolve() ?: return null
          when {
            resolved is PsiClass -> resolved.qualifiedName
            else -> PsiTypesUtil.getPsiClass((resolved as? UMethod)?.returnType)?.qualifiedName
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

  private companion object {
    val LOG_RUN_KEY: Key<SplitModeInspectionLogRun> = Key.create("SplitModeApiUsageInspection.logRun")
  }
}
