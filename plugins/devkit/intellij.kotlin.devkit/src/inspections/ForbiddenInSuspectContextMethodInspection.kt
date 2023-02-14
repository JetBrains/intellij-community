// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.KotlinType

private const val REQUIRES_SUSPEND_CONTEXT_ANNOTATION = "com.intellij.util.concurrency.annotations.RequiresBlockingContext"
private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val RESTRICTS_SUSPENSION = "kotlin.coroutines.RestrictsSuspension"

private val progressManagerCheckedCanceledName = FqName(PROGRESS_MANAGER_CHECKED_CANCELED)
private val restrictsSuspensionName = FqName(RESTRICTS_SUSPENSION)

private const val COROUTINE_CHECK_CANCELED_FIX = "com.intellij.openapi.progress.checkCanceled"

@Internal
class ForbiddenInSuspectContextMethodInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return myVisitor(holder)
  }

  private fun myVisitor(holder: ProblemsHolder): PsiElementVisitor {
    val blockingContextCallsVisitor by lazy(mode = LazyThreadSafetyMode.NONE) {
      BlockingContextMethodsCallsVisitor(holder)
    }

    return object : KtTreeVisitorVoid() {
      override fun visitElement(element: PsiElement): Unit = Unit

      override fun visitNamedFunction(function: KtNamedFunction) {
        if (!function.hasModifier(KtTokens.SUSPEND_KEYWORD) || isSuspensionRestricted(function)) {
          super.visitNamedFunction(function)
          return
        }
        function.bodyExpression?.accept(blockingContextCallsVisitor)
      }

      override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        val bindingContext = lambdaExpression.safeAnalyze()
        val typeInfo = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, lambdaExpression]
        val type = typeInfo?.type
        if (type != null && type.isSuspendFunctionType && !isSuspensionRestricted(type)) {
          lambdaExpression.bodyExpression?.accept(blockingContextCallsVisitor)
          return
        }

        super.visitLambdaExpression(lambdaExpression)
      }
    }
  }

  class BlockingContextMethodsCallsVisitor(
    private val holder: ProblemsHolder,
  ) : KtTreeVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
      val resolutionFacade = expression.getResolutionFacade()
      val resolvedCall = expression.resolveToCall(resolutionFacade) ?: return

      val callDescriptor = resolvedCall.resultingDescriptor
      val hasAnnotation = callDescriptor.annotations
        .hasAnnotation(FqName(REQUIRES_SUSPEND_CONTEXT_ANNOTATION))

      if (!hasAnnotation) return

      when (callDescriptor.fqNameOrNull()) {
        progressManagerCheckedCanceledName -> {
          holder.registerProblem(
            expression.getCallNameExpression() ?: expression,
            DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.check.canceled.text"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            ReplaceProgressManagerCheckCanceledQuickFix
          )
        }
        else -> {
          holder.registerProblem(
            expression.getCallNameExpression() ?: expression,
            DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.text", callDescriptor.name.asString()),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          )
        }
      }
    }

    override fun visitDeclaration(dcl: KtDeclaration) {
    }
  }

  private object ReplaceProgressManagerCheckCanceledQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = DevKitKotlinBundle.message(
      "inspections.forbidden.method.in.suspend.context.check.canceled.fix.text")

    override fun getName(): String = familyName

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val startElement = descriptor.startElement as? KtElement ?: return
      val callExpression = startElement.getParentOfType<KtCallExpression>(false) ?: return
      val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
      val factory = KtPsiFactory(project)
      val suspendAwareCheckCanceled = factory.createExpression("$COROUTINE_CHECK_CANCELED_FIX()")
      val resultExpression = if (qualifiedExpression != null) {
        qualifiedExpression.replace(suspendAwareCheckCanceled)
      }
      else {
        callExpression.replace(suspendAwareCheckCanceled)
      }
      ShortenReferences.DEFAULT.process(resultExpression as KtElement)
    }
  }
}

private fun isSuspensionRestricted(function: KtNamedFunction): Boolean {
  val resolutionFacade = function.getResolutionFacade()

  val declaringClass = function.containingClass()
  if (declaringClass != null) {
    val classDescriptor = declaringClass.safeAnalyze(resolutionFacade)[BindingContext.CLASS, declaringClass]
    if (classDescriptor?.restrictsSuspension() == true) {
      return true
    }
  }

  val receiver = function.receiverTypeReference
  if (receiver != null) {
    val typeDescriptor = receiver.safeAnalyze(resolutionFacade)[BindingContext.TYPE, receiver]
    if (typeDescriptor != null && typeDescriptor.constructor.declarationDescriptor?.restrictsSuspension() == true) {
      return true
    }
  }

  return false
}

private fun isSuspensionRestricted(lambdaType: KotlinType): Boolean {
  assert(lambdaType.isSuspendFunctionType)

  if (!lambdaType.annotations.any { it.fqName == StandardNames.FqNames.extensionFunctionType }) return false
  val extensionType = lambdaType.arguments.firstOrNull() ?: return false
  return extensionType.type.constructor.declarationDescriptor?.restrictsSuspension() == true
}

private fun ClassifierDescriptor.restrictsSuspension(): Boolean =
  annotations.any { it.fqName == restrictsSuspensionName }