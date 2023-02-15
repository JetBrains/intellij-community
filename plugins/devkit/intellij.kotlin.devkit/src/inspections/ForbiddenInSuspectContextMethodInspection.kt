// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

private const val REQUIRES_SUSPEND_CONTEXT_ANNOTATION = "com.intellij.util.concurrency.annotations.RequiresBlockingContext"
private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val RESTRICTS_SUSPENSION = "kotlin.coroutines.RestrictsSuspension"

private val progressManagerCheckedCanceledName = FqName(PROGRESS_MANAGER_CHECKED_CANCELED)
private val restrictsSuspensionName = FqName(RESTRICTS_SUSPENSION)

private const val COROUTINE_CHECK_CANCELED_FIX = "com.intellij.openapi.progress.checkCancelled"

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
        analyze(lambdaExpression) {
          val type = lambdaExpression.getKtType()
          if (type?.isSuspendFunctionType == true && !isSuspensionRestricted(type)) {
            lambdaExpression.bodyExpression?.accept(blockingContextCallsVisitor)
            return
          }
        }

        super.visitLambdaExpression(lambdaExpression)
      }
    }
  }

  class BlockingContextMethodsCallsVisitor(
    private val holder: ProblemsHolder,
  ) : KtTreeVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
      analyze(expression) {
        val calledSymbol = expression.resolveCall().singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol

        if (calledSymbol !is KtNamedSymbol) return
        val hasAnnotation = calledSymbol.hasAnnotation(ClassId.topLevel(FqName(REQUIRES_SUSPEND_CONTEXT_ANNOTATION)))

        if (!hasAnnotation) return

        when (calledSymbol.callableIdIfNonLocal?.asSingleFqName()) {
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
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.text", calledSymbol.name.asString()),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
          }
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
      ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
    }
  }
}

private fun isSuspensionRestricted(function: KtNamedFunction): Boolean {
  analyze(function) {
    val declaringClass = function.containingClass()
    val declaringClassSymbol = declaringClass?.getClassOrObjectSymbol()
    if (declaringClassSymbol != null && restrictsSuspension(declaringClassSymbol)) {
      return true
    }

    val receiverType = function.receiverTypeReference
    val receiverTypeSymbol = receiverType?.getKtType()?.expandedClassSymbol
    if (receiverTypeSymbol != null && restrictsSuspension(receiverTypeSymbol)) {
      return true
    }

    return false
  }
}

private fun KtAnalysisSession.isSuspensionRestricted(lambdaType: KtType): Boolean {
  assert(lambdaType.isSuspendFunctionType)

  val receiverTypeSymbol = (lambdaType as? KtFunctionalType)?.receiverType?.expandedClassSymbol
  return receiverTypeSymbol != null && restrictsSuspension(receiverTypeSymbol)
}

private fun KtAnalysisSession.restrictsSuspension(symbol: KtClassOrObjectSymbol): Boolean =
  symbol.hasAnnotation(ClassId.topLevel(restrictsSuspensionName))