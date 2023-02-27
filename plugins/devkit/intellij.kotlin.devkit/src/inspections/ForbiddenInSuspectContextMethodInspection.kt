// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.util.ImportInsertHelperImpl
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

private val REQUIRES_SUSPEND_CONTEXT_ANNOTATION = RequiresBlockingContext::class.java.canonicalName
private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val APPLICATION_INVOKE_AND_WAIT = "com.intellij.openapi.application.Application.invokeAndWait"
private const val RESTRICTS_SUSPENSION = "kotlin.coroutines.RestrictsSuspension"
private const val INTELLIJ_EDT_DISPATCHER = "com.intellij.openapi.application.EDT"

private val progressManagerCheckedCanceledName = FqName(PROGRESS_MANAGER_CHECKED_CANCELED)
private val applicationInvokeAndWaitName = FqName(APPLICATION_INVOKE_AND_WAIT)
private val restrictsSuspensionName = FqName(RESTRICTS_SUSPENSION)
private val intelliJEdtDispatcher = FqName(INTELLIJ_EDT_DISPATCHER)

private const val COROUTINE_CHECK_CANCELED_FIX = "com.intellij.openapi.progress.checkCancelled"
private const val WITH_CONTEXT = "kotlinx.coroutines.withContext"
private const val DISPATCHERS = "kotlinx.coroutines.Dispatchers"

@Internal
internal class ForbiddenInSuspectContextMethodInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return createFileVisitor(holder)
  }

  private fun createFileVisitor(holder: ProblemsHolder): PsiElementVisitor {
    val blockingContextCallsVisitor by lazy(mode = LazyThreadSafetyMode.NONE) {
      BlockingContextMethodsCallsVisitor(holder)
    }

    return object : KtTreeVisitorVoid() {
      override fun visitElement(element: PsiElement): Unit = Unit

      override fun visitNamedFunction(function: KtNamedFunction) {
        if (function.hasModifier(KtTokens.SUSPEND_KEYWORD) && !isSuspensionRestricted(function)) {
          function.bodyExpression?.accept(blockingContextCallsVisitor)
          return
        }
        super.visitNamedFunction(function)
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
        val functionCall = expression.resolveCall().singleFunctionCallOrNull()
        val calledSymbol = functionCall?.partiallyAppliedSymbol?.symbol

        if (calledSymbol !is KtNamedSymbol) return
        val hasAnnotation = calledSymbol.hasAnnotation(ClassId.topLevel(FqName(REQUIRES_SUSPEND_CONTEXT_ANNOTATION)))

        if (!hasAnnotation) {
          if (calledSymbol is KtFunctionSymbol && calledSymbol.isInline) {
            checkInlineLambdaArguments(functionCall)
          }
          return
        }

        when (calledSymbol.callableIdIfNonLocal?.asSingleFqName()) {
          progressManagerCheckedCanceledName -> {
            holder.registerProblem(
              extractElementToHighlight(expression),
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.check.canceled.text"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              ReplaceProgressManagerCheckCanceledQuickFix(expression)
            )
          }
          applicationInvokeAndWaitName -> {
            holder.registerProblem(
              extractElementToHighlight(expression),
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.invoke.and.wait.text"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              ReplaceInvokeAndWaitWithWithContextQuickFix(expression)
            )
          }
          else -> {
            holder.registerProblem(
              extractElementToHighlight(expression),
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.text", calledSymbol.name.asString()),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
          }
        }
      }
    }

    private fun extractElementToHighlight(expression: KtCallExpression) = expression.getCallNameExpression() ?: expression

    private fun checkInlineLambdaArguments(call: KtFunctionCall<*>) {
      for ((psi, descriptor) in call.argumentMapping) {
        if (
          descriptor.returnType is KtFunctionalType &&
          !descriptor.symbol.isCrossinline &&
          !descriptor.symbol.isNoinline &&
          psi is KtLambdaExpression
        ) {
          psi.bodyExpression?.accept(this)
        }
      }
    }

    override fun visitDeclaration(dcl: KtDeclaration): Unit = Unit
  }

  private class ReplaceProgressManagerCheckCanceledQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = DevKitKotlinBundle.message(
      "inspections.forbidden.method.in.suspend.context.check.canceled.fix.text")

    override fun getText(): String = familyName

    override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
      return startElement.getParentOfType<KtCallExpression>(false) != null
    }

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
      val callExpression = startElement.getParentOfType<KtCallExpression>(false)!!
      val factory = KtPsiFactory(project)
      val suspendAwareCheckCanceled = factory.createExpression("$COROUTINE_CHECK_CANCELED_FIX()")
      val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
      val resultExpression = if (qualifiedExpression != null) {
        qualifiedExpression.replace(suspendAwareCheckCanceled)
      }
      else {
        callExpression.replace(suspendAwareCheckCanceled)
      }
      ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
    }
  }

  private class ReplaceInvokeAndWaitWithWithContextQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = DevKitKotlinBundle.message(
      "inspections.forbidden.method.in.suspend.context.invoke.and.wait.fix.text")

    override fun getText(): String = familyName

    override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
      val callExpression = startElement.getParentOfType<KtCallExpression>(false) ?: return false
      return callExpression.valueArguments.firstOrNull()?.getArgumentExpression() is KtLambdaExpression
    }

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
      val callExpression = startElement.getParentOfType<KtCallExpression>(false)!!

      if (!isImported(FqName("com.intellij.openapi.application.EDT"), callExpression.containingKtFile)) {
        ImportInsertHelperImpl.addImport(project, callExpression.containingKtFile, intelliJEdtDispatcher)
      }

      val factory = KtPsiFactory(project)
      val expression = factory.createExpression("$WITH_CONTEXT($DISPATCHERS.${intelliJEdtDispatcher.shortName().asString()}) {}")
      val argument = callExpression.valueArguments[0]
      (expression as KtQualifiedExpression).selectorExpression
        .let { it as KtCallExpression }
        .lambdaArguments
        .first()
        .getLambdaExpression()!!
        .replace(argument.getArgumentExpression()!!)

      val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
      val resultExpression = if (qualifiedExpression != null) {
        qualifiedExpression.replace(expression)
      }
      else {
        callExpression.replace(expression)
      }

      ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
    }
  }
}

private fun isImported(name: FqName, file: KtFile): Boolean {
  if (name.parent() == file.packageFqName) return true
  return file.importDirectives.mapNotNull { it.importPath }.any { name.isImported(it) }
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
    return receiverTypeSymbol != null && restrictsSuspension(receiverTypeSymbol)
  }
}

private fun KtAnalysisSession.isSuspensionRestricted(lambdaType: KtType): Boolean {
  assert(lambdaType.isSuspendFunctionType)

  val receiverTypeSymbol = (lambdaType as? KtFunctionalType)?.receiverType?.expandedClassSymbol
  return receiverTypeSymbol != null && restrictsSuspension(receiverTypeSymbol)
}

private fun restrictsSuspension(symbol: KtClassOrObjectSymbol): Boolean =
  symbol.hasAnnotation(ClassId.topLevel(restrictsSuspensionName))