// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.util.containers.toArray
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.isInspectionForBlockingContextAvailable
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

private const val PROGRESS_MANAGER_CHECKED_CANCELED = "com.intellij.openapi.progress.ProgressManager.checkCanceled"
private const val APPLICATION_INVOKE_AND_WAIT = "com.intellij.openapi.application.Application.invokeAndWait"
private const val INVOKE_AND_WAIT_IF_NEEDED = "com.intellij.openapi.application.invokeAndWaitIfNeeded"
private const val APPLICATION_INVOKE_LATER = "com.intellij.openapi.application.Application.invokeLater"
private const val INVOKE_LATER_KT = "com.intellij.openapi.application.invokeLater"
private const val MODALITY_STATE_DEFAULT_MODALITY_STATE = "com.intellij.openapi.application.ModalityState.defaultModalityState"
private const val APPLICATION_GET_DEFAULT_MODALITY_STATE = "com.intellij.openapi.application.Application.getDefaultModalityState"
private const val RESTRICTS_SUSPENSION = "kotlin.coroutines.RestrictsSuspension"
private const val INTELLIJ_EDT_DISPATCHER = "com.intellij.openapi.application.EDT"
private const val LAUNCH = "kotlinx.coroutines.launch"

private val progressManagerCheckedCanceledName = FqName(PROGRESS_MANAGER_CHECKED_CANCELED)
private val applicationInvokeAndWaitName = FqName(APPLICATION_INVOKE_AND_WAIT)
private val invokeAndWaitIfNeeded = FqName(INVOKE_AND_WAIT_IF_NEEDED)
private val applicationInvokeLater = FqName(APPLICATION_INVOKE_LATER)
private val invokeLaterKt = FqName(INVOKE_LATER_KT)
private val modalityStateDefaultModalityState = FqName(MODALITY_STATE_DEFAULT_MODALITY_STATE)
private val applicationGetDefaultModalityState = FqName(APPLICATION_GET_DEFAULT_MODALITY_STATE)
private val restrictsSuspensionName = FqName(RESTRICTS_SUSPENSION)
private val intelliJEdtDispatcher = FqName(INTELLIJ_EDT_DISPATCHER)
private val coroutinesLaunch = FqName(LAUNCH)

private const val COROUTINE_CHECK_CANCELED_FIX = "com.intellij.openapi.progress.checkCancelled"
private const val WITH_CONTEXT = "kotlinx.coroutines.withContext"
private const val DISPATCHERS = "kotlinx.coroutines.Dispatchers"
private const val COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope"

internal class ForbiddenInSuspectContextMethodInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return if (isInspectionForBlockingContextAvailable(holder)) {
      createFileVisitor(holder)
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }
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
          val type = lambdaExpression.expressionType
          if (type?.isSuspendFunctionType == true && !isSuspensionRestricted(type)) {
            lambdaExpression.bodyExpression?.accept(blockingContextCallsVisitor)
            return
          }
        }

        super.visitLambdaExpression(lambdaExpression)
      }
    }
  }

  private class BlockingContextMethodsCallsVisitor(
    private val holder: ProblemsHolder,
  ) : BlockingContextFunctionBodyVisitor() {
    private val visitedSymbols = mutableSetOf<KaSymbol>()
    private var callingElement: KtCallExpression? = null

    override fun visitCallExpression(expression: KtCallExpression) {
      analyze(expression) {
        val functionCall = expression.resolveToCall()?.singleFunctionCallOrNull()
        val calledSymbol = functionCall?.partiallyAppliedSymbol?.symbol

        if (calledSymbol !is KaNamedSymbol) return
        val hasAnnotation = RequiresBlockingContextAnnotationId in calledSymbol.annotations

        if (!hasAnnotation) {
          if (calledSymbol is KaNamedFunctionSymbol) {
            checkCalledFunction(expression, calledSymbol)
          }
          if (calledSymbol is KaNamedFunctionSymbol && calledSymbol.isInline) {
            checkInlineLambdaArguments(functionCall)
          }
          return super.visitCallExpression(expression)
        }

        when (calledSymbol.callableId?.asSingleFqName()) {
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
            )
          }
          applicationInvokeLater, invokeLaterKt -> {
            holder.registerProblem(
              extractElementToHighlight(expression),
              DevKitKotlinBundle.message("inspections.forbidden.method.in.suspend.context.invoke.later.text"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
              *provideFixesForInvokeLater(expression)
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

    private fun checkCalledFunction(call: KtCallExpression, calledSymbol: KaNamedFunctionSymbol) {
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

    private fun KaSession.provideFixesForInvokeLater(callExpression: KtCallExpression): Array<LocalQuickFix> {
      callingElement?.let { return arrayOf(NavigateToCallInSuspendFunction(it)) }

      return buildList<LocalQuickFix> {
        add(ReplaceInvokeLaterWithWithContextQuickFix(callExpression))

        val implicitReceiverTypesAtPosition = collectImplicitReceiverTypes(callExpression)
        val coroutineScopeClassId = ClassId.topLevel(FqName(COROUTINE_SCOPE))
        val hasCoroutineScope = implicitReceiverTypesAtPosition.any { it.isSubtypeOf(coroutineScopeClassId) }
        if (hasCoroutineScope) {
          add(ReplaceInvokeLaterWithLaunchQuickFix(callExpression))
        }
      }.toArray(LocalQuickFix.EMPTY_ARRAY)
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

    private open class ReplaceInvokeAndWaitWithWithContextQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(
      element) {
      override fun getFamilyName(): String = DevKitKotlinBundle.message(
        "inspections.forbidden.method.in.suspend.context.invoke.and.wait.fix.text")

      override fun getText(): String = familyName

      override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
        val callExpression = getCallExpression(startElement) ?: return false
        return getLambdaArgumentExpression(callExpression) != null
      }

      override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val callExpression = getCallExpression(startElement)!!

        val ktFile = callExpression.containingKtFile
        if (!isImported(intelliJEdtDispatcher, ktFile)) {
          ktFile.addImport(intelliJEdtDispatcher)
        }

        replaceMethodInCallWithLambda(callExpression, "$WITH_CONTEXT($DISPATCHERS.${intelliJEdtDispatcher.shortName().asString()}) {}")
      }
    }

    private class ReplaceInvokeLaterWithWithContextQuickFix(element: PsiElement) : ReplaceInvokeAndWaitWithWithContextQuickFix(element) {
      override fun getFamilyName(): String = DevKitKotlinBundle.message(
        "inspections.forbidden.method.in.suspend.context.invoke.later.fix.with.context.text")
    }

    private class ReplaceInvokeLaterWithLaunchQuickFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
      override fun getFamilyName(): String = DevKitKotlinBundle.message(
        "inspections.forbidden.method.in.suspend.context.invoke.later.fix.launch.text")

      override fun getText(): String = familyName

      override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
        val callExpression = getCallExpression(startElement) ?: return false
        return getLambdaArgumentExpression(callExpression) != null
      }

      override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val callExpression = getCallExpression(startElement)!!

        val ktFile = callExpression.containingKtFile
        if (!isImported(intelliJEdtDispatcher, ktFile)) {
          ktFile.addImport(intelliJEdtDispatcher)
        }
        if (!isImported(coroutinesLaunch, callExpression.containingKtFile)) {
          ktFile.addImport(coroutinesLaunch)
        }

        replaceMethodInCallWithLambda(callExpression,
                                      "${coroutinesLaunch.shortName()}($DISPATCHERS.${intelliJEdtDispatcher.shortName()}) {}")
      }
    }

    private class NavigateToCallInSuspendFunction(callingElement: KtCallExpression) : IntentionAndQuickFixAction() {
      private val pointer: SmartPsiElementPointer<KtCallExpression> = SmartPointerManager.createPointer(callingElement)

      override fun getFamilyName(): String = DevKitKotlinBundle.message(
        "inspections.forbidden.method.in.suspend.context.navigate.to.suspend.context")

      override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && pointer.element != null
      }

      override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
        extractElementToHighlight(pointer.element!!).navigate(true)
      }

      override fun startInWriteAction(): Boolean = false

      override fun getName(): String = familyName
    }
  }
}

private fun getCallExpression(startElement: PsiElement): KtCallExpression? =
  startElement.getParentOfType<KtCallExpression>(false)

private fun getLambdaArgumentExpression(callExpression: KtCallExpression): KtLambdaExpression? {
  val lambdaExpression = callExpression.lambdaArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression
  if (lambdaExpression != null) return lambdaExpression
  return callExpression.valueArguments.firstOrNull()?.getArgumentExpression() as? KtLambdaExpression
}

private fun replaceMethodInCallWithLambda(callExpression: KtCallExpression, newCallPattern: String) {
  val factory = KtPsiFactory(callExpression.project)
  val expression = factory.createExpression(newCallPattern)
  val argument = getLambdaArgumentExpression(callExpression)
  (expression as? KtQualifiedExpression)?.selectorExpression?.let { it as KtCallExpression }
    .let { it ?: expression as KtCallExpression }
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

private fun isImported(name: FqName, file: KtFile): Boolean {
  if (name.parent() == file.packageFqName) return true
  return file.importDirectives.mapNotNull { it.importPath }.any { name.isImported(it) }
}

private fun isSuspensionRestricted(function: KtNamedFunction): Boolean {
  analyze(function) {
    val declaringClass = function.containingClass()
    val declaringClassSymbol = declaringClass?.classSymbol
    if (declaringClassSymbol != null && restrictsSuspension(declaringClassSymbol)) {
      return true
    }

    val receiverType = function.receiverTypeReference
    val receiverTypeSymbol = receiverType?.type?.expandedSymbol
    return receiverTypeSymbol != null && restrictsSuspension(receiverTypeSymbol)
  }
}

private fun KaSession.isSuspensionRestricted(lambdaType: KaType): Boolean {
  assert(lambdaType.isSuspendFunctionType)

  val receiverTypeSymbol = (lambdaType as? KaFunctionType)?.receiverType?.expandedSymbol
  return receiverTypeSymbol != null && restrictsSuspension(receiverTypeSymbol)
}

private fun restrictsSuspension(symbol: KaClassSymbol): Boolean =
  ClassId.topLevel(restrictsSuspensionName) in symbol.annotations