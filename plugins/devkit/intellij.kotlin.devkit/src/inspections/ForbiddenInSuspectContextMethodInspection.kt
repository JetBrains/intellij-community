// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
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
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
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
import org.jetbrains.kotlin.psi.psiUtil.*

private val REQUIRES_SUSPEND_CONTEXT_ANNOTATION = RequiresBlockingContext::class.java.canonicalName
private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val APPLICATION_INVOKE_AND_WAIT = "com.intellij.openapi.application.Application.invokeAndWait"
private const val INVOKE_AND_WAIT_IF_NEEDED = "com.intellij.openapi.application.invokeAndWaitIfNeeded"
private const val MODALITY_STATE_DEFAULT_MODALITY_STATE = "com.intellij.openapi.application.ModalityState.defaultModalityState"
private const val APPLICATION_GET_DEFAULT_MODALITY_STATE = "com.intellij.openapi.application.Application.getDefaultModalityState"
private const val RESTRICTS_SUSPENSION = "kotlin.coroutines.RestrictsSuspension"
private const val INTELLIJ_EDT_DISPATCHER = "com.intellij.openapi.application.EDT"
private const val CONTEXT_MODALITY_EXT = "com.intellij.openapi.application.contextModality"

private val requiresSuspendContextAnnotation = FqName(REQUIRES_SUSPEND_CONTEXT_ANNOTATION)
private val progressManagerCheckedCanceledName = FqName(PROGRESS_MANAGER_CHECKED_CANCELED)
private val applicationInvokeAndWaitName = FqName(APPLICATION_INVOKE_AND_WAIT)
private val invokeAndWaitIfNeeded = FqName(INVOKE_AND_WAIT_IF_NEEDED)
private val modalityStateDefaultModalityState = FqName(MODALITY_STATE_DEFAULT_MODALITY_STATE)
private val applicationGetDefaultModalityState = FqName(APPLICATION_GET_DEFAULT_MODALITY_STATE)
private val restrictsSuspensionName = FqName(RESTRICTS_SUSPENSION)
private val intelliJEdtDispatcher = FqName(INTELLIJ_EDT_DISPATCHER)
private val contextModalityExt = FqName(CONTEXT_MODALITY_EXT)

private const val COROUTINE_CHECK_CANCELED_FIX = "com.intellij.openapi.progress.checkCancelled"
private const val WITH_CONTEXT = "kotlinx.coroutines.withContext"
private const val DISPATCHERS = "kotlinx.coroutines.Dispatchers"
private const val CURRENT_COROUTINE_CONTEXT = "kotlinx.coroutines.currentCoroutineContext"
private const val KOTLIN_TODO = "kotlin.TODO"

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
    private val visitedSymbols = mutableSetOf<KtSymbol>()
    private var callingElement: KtCallExpression? = null

    override fun visitCallExpression(expression: KtCallExpression) {
      analyze(expression) {
        val functionCall = expression.resolveCall().singleFunctionCallOrNull()
        val calledSymbol = functionCall?.partiallyAppliedSymbol?.symbol

        if (calledSymbol !is KtNamedSymbol) return
        val hasAnnotation = calledSymbol.hasAnnotation(ClassId.topLevel(requiresSuspendContextAnnotation))

        if (!hasAnnotation) {
          if (calledSymbol is KtFunctionSymbol) {
            checkCalledFunction(expression, calledSymbol)
          }
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
              ifInSuspend { ReplaceProgressManagerCheckCanceledQuickFix(expression) }
            )
          }
          applicationInvokeAndWaitName, invokeAndWaitIfNeeded -> {
            holder.registerProblem(
              extractElementToHighlight(expression),
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.invoke.and.wait.text"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              ifInSuspend { ReplaceInvokeAndWaitWithWithContextQuickFix(expression) }
            )
          }
          applicationGetDefaultModalityState, modalityStateDefaultModalityState -> {
            holder.registerProblem(
              extractElementToHighlight(expression),
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.default.modality.state.text"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              ifInSuspend { ReplaceDefaultModalityStateWithCurrentModalityQuickFix(expression) }
            )
          }
          else -> {
            holder.registerProblem(
              extractElementToHighlight(expression),
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.text", calledSymbol.name.asString()),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              *generalFixes()
            )
          }
        }
      }
    }

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

    private fun checkCalledFunction(call: KtCallExpression, calledSymbol: KtFunctionSymbol) {
      // Visiting functions in current file. We don't visit it with our visitor in other cases, so it will be visited once
      if (
        calledSymbol.psi?.containingFile == holder.file &&
        !calledSymbol.isSuspend &&
        calledSymbol !in visitedSymbols &&
        callingElement == null
      ) {
        try {
          visitedSymbols += calledSymbol
          callingElement = call
          (calledSymbol.psi as? KtNamedFunction)?.bodyExpression?.accept(this)
        }
        finally {
          callingElement = null
        }
      }
    }

    private inline fun ifInSuspend(fix: () -> LocalQuickFix): LocalQuickFix {
      return callingElement?.let { NavigateToCallInSuspendFunction(it) } ?: fix()
    }

    private fun generalFixes(): Array<LocalQuickFix> {
      return callingElement?.let { arrayOf(NavigateToCallInSuspendFunction(it)) } ?: LocalQuickFix.EMPTY_ARRAY
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression): Unit = Unit

    override fun visitDeclaration(dcl: KtDeclaration) {
      if (dcl is KtVariableDeclaration) {
        dcl.initializer?.accept(this)
      }
    }
  }

  private class ReplaceProgressManagerCheckCanceledQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = DevKitKotlinBundle.message(
      "inspections.forbidden.method.in.suspend.context.check.canceled.fix.text")

    override fun getText(): String = familyName

    override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean =
      getCallExpression(startElement) != null

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
      val callExpression = getCallExpression(startElement)!!
      val factory = KtPsiFactory(project)
      val suspendAwareCheckCanceled = factory.createExpression("$COROUTINE_CHECK_CANCELED_FIX()")
      val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
      val expressionToReplace = qualifiedExpression ?: callExpression
      val resultExpression = expressionToReplace.replace(suspendAwareCheckCanceled)
      ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
    }
  }

  private class ReplaceInvokeAndWaitWithWithContextQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = DevKitKotlinBundle.message(
      "inspections.forbidden.method.in.suspend.context.invoke.and.wait.fix.text")

    override fun getText(): String = familyName

    override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
      val callExpression = getCallExpression(startElement) ?: return false
      return getLambdaArgumentExpression(callExpression) != null
    }

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
      val callExpression = getCallExpression(startElement)!!

      if (!isImported(FqName("com.intellij.openapi.application.EDT"), callExpression.containingKtFile)) {
        ImportInsertHelperImpl.addImport(project, callExpression.containingKtFile, intelliJEdtDispatcher)
      }

      val factory = KtPsiFactory(project)
      val expression = factory.createExpression("$WITH_CONTEXT($DISPATCHERS.${intelliJEdtDispatcher.shortName().asString()}) {}")
      val argument = getLambdaArgumentExpression(callExpression)
      (expression as KtQualifiedExpression).selectorExpression
        .let { it as KtCallExpression }
        .lambdaArguments
        .first()
        .getLambdaExpression()!!
        .replace(argument!!)

      val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
      val resultExpression = if (qualifiedExpression != null) {
        qualifiedExpression.replace(expression)
      }
      else {
        callExpression.replace(expression)
      }

      ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
    }

    private fun getLambdaArgumentExpression(callExpression: KtCallExpression): KtExpression? {
      val lambdaExpression = callExpression.lambdaArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression
      if (lambdaExpression != null) return lambdaExpression
      return callExpression.valueArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression
    }
  }

  private class ReplaceDefaultModalityStateWithCurrentModalityQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(
    element
  ) {
    override fun getFamilyName(): String = DevKitKotlinBundle.message(
      "inspections.forbidden.method.in.suspend.context.default.modality.state.fix.text")

    override fun getText(): String = familyName

    override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean =
      getCallExpression(startElement) != null

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
      val callExpression = getCallExpression(startElement)!!
      val factory = KtPsiFactory(project)

      if (!isImported(contextModalityExt, callExpression.containingKtFile)) {
        ImportInsertHelperImpl.addImport(project, callExpression.containingKtFile, contextModalityExt)
      }

      // ?: because defaultModalityState is @NotNull, so it can be unexpected to replace with something nullable
      val contextModalityCall = factory.createExpression(
        "$CURRENT_COROUTINE_CONTEXT().${contextModalityExt.shortName()}() ?: $KOTLIN_TODO(\"Handle absence of ModalityState\")"
      )
      val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
      val expressionToReplace = qualifiedExpression ?: callExpression
      val resultExpression = expressionToReplace.replace(contextModalityCall)
      ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
    }
  }

  private class NavigateToCallInSuspendFunction(callingElement: KtCallExpression) : IntentionAndQuickFixAction() {
    private val pointer: SmartPsiElementPointer<KtCallExpression> = SmartPointerManager.createPointer(callingElement)

    override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.navigate.to.suspend.context")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
      return editor != null && pointer.element != null
    }

    override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
      extractElementToHighlight(pointer.element!!).navigate (true)
    }

    override fun startInWriteAction(): Boolean = false

    override fun getName(): String = familyName
  }
}

private fun getCallExpression(startElement: PsiElement): KtCallExpression? =
  startElement.getParentOfType<KtCallExpression>(false)

private fun extractElementToHighlight(expression: KtCallExpression): KtElement = expression.getCallNameExpression() ?: expression

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