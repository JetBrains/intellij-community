// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiDisjunctionType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.startOffset
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.quickfix.CancellationExceptionHandlingFixProvider
import org.jetbrains.idea.devkit.inspections.quickfix.cancellationExceptionHandlingFixes
import org.jetbrains.idea.devkit.inspections.quickfix.isRethrowHelperAvailable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.resolveToUElementOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val LOGGER_CLASS_NAME = "com.intellij.openapi.diagnostic.Logger"
private const val RETHROW_FUN = "rethrowControlFlowException"

internal class IncorrectCancellationExceptionHandlingInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (node.receiverType?.isClassType(LOGGER_CLASS_NAME) != true) {
          return super.visitCallExpression(node)
        }

        for (arg in node.valueArguments) {
          val rawArgType = arg.getExpressionType() ?: continue
          val argTypes = rawArgType.expandDisjunctions()
          for (argType in argTypes) {
            if (argType.isControlFlowExceptionType() && reportExceptionLogging(node, argType, loggingFixes(node, arg, argTypes))) {
              return super.visitCallExpression(node)
            }
          }
        }
        return super.visitCallExpression(node)
      }

      private fun reportExceptionLogging(node: UCallExpression, argType: PsiType, fixes: Array<LocalQuickFix>): Boolean {
        val loggedClassName = argType.asSafely<PsiClassType>()?.resolve()?.qualifiedName ?: return false
        // 'as UExpression' selects the UExpression overload (anchors the whole call); the UCallExpression overload would
        // anchor only the method identifier.
        holder.registerUProblem(node as UExpression, message("inspections.incorrect.exception.logged.name", loggedClassName), *fixes)
        return true
      }

      /**
       * Fixes for a log of [loggedArgument], whose type alternatives are [loggedTypes].
       *
       * The log is removed only when every alternative is a control-flow exception. A multi-catch parameter such as
       * `IllegalArgumentException | ProcessCanceledException` also carries failures that must stay logged, so the
       * cancellation is rethrown before the log instead.
       */
      private fun loggingFixes(node: UCallExpression, loggedArgument: UExpression, loggedTypes: List<PsiType>): Array<LocalQuickFix> {
        if (loggedTypes.all { it.isControlFlowExceptionType() }) {
          return removeLoggingFixes(node)
        }
        val loggedParameter = loggedArgument.skipParenthesizedExprDown().asSafely<USimpleNameReferenceExpression>()?.resolveToUElement()
        if (loggedParameter is UParameter && loggedParameter.uastParent is UCatchClause) {
          return insertHelperFixes(loggedParameter)
        }
        return LocalQuickFix.EMPTY_ARRAY
      }

      override fun visitCatchClause(node: UCatchClause): Boolean {
        val catchParameters = node.parameters
        val caughtCeInfo = findSuspiciousCeCaughtParam(catchParameters)
        if (caughtCeInfo != null) {
          inspectIncorrectCeHandling(node, caughtCeInfo)
        }
        else {
          inspectGenericThrowableIfAnyOfTryStatementsThrowsCe(node, catchParameters)
        }
        return super.visitCatchClause(node)
      }

      private fun findSuspiciousCeCaughtParam(catchParameters: List<UParameter>): CaughtCeInfo? {
        for (catchParameter in catchParameters) {
          // language-specific check (e.g. Kotlin 'CancellationException' in a suspending context):
          val checker = cancellationExceptionHandlingChecker(catchParameter.lang)
          val sourcePsi = catchParameter.sourcePsi
          if (sourcePsi != null && checker?.isSuspicious(sourcePsi) == true) {
            return CaughtCeInfo(checker.getCeName(), catchParameter)
          }
          // general UAST check: a 'ProcessCanceledException' is always the cancellation of the current computation.
          // Other control-flow exceptions may be handled by design, and a plain 'CancellationException' may belong to an
          // awaited task, so those are reported only when a call in the 'try' is proven to throw the current cancellation.
          for (caughtExceptionType in catchParameter.type.expandDisjunctions()) {
            if (caughtExceptionType !is PsiClassType) continue
            if (caughtExceptionType.isProcessCanceledExceptionType()) {
              val name = caughtExceptionType.resolve()?.qualifiedName ?: continue
              return CaughtCeInfo(name, catchParameter)
            }
          }
        }
        return null
      }

      private fun PsiType.isInheritorOrSelf(psiClass: PsiClass): Boolean {
        val psiClassType = this as? PsiClassType ?: return false
        val checkedPsiClass = psiClassType.resolve() ?: return false
        return InheritanceUtil.isInheritorOrSelf(checkedPsiClass, psiClass, true)
      }

      private fun PsiType.expandDisjunctions(): List<PsiType> {
        return asSafely<PsiDisjunctionType>()?.disjunctions ?: listOf(this)
      }

      private fun inspectIncorrectCeHandling(node: UCatchClause, caughtCeInfo: CaughtCeInfo) {
        if (!ceIsRethrown(node.body, caughtCeInfo.parameter)) {
          holder.registerUProblem(caughtCeInfo.parameter,
                                  message("inspections.incorrect.cancellation.exception.handling.name.not.rethrown",
                                          caughtCeInfo.ceClassName),
                                  *insertHelperFixes(caughtCeInfo.parameter))
        }
      }

      private fun inspectGenericThrowableIfAnyOfTryStatementsThrowsCe(
        catchClause: UCatchClause,
        catchParameters: List<UParameter>,
      ) {
        val tryExpression = catchClause.getParentOfType<UTryExpression>() ?: return
        if (tryExpression.containsInterceptingCatchClause() || tryExpression.checkContainsSuspiciousCeCatchClause()) {
          // Cancellation exception will be caught by the explicit catch clause
          return
        }
        val caughtGenericThrowableParam = catchParameters.firstOrNull { param ->
          param.type.expandDisjunctions().any { it.isProcessCanceledExceptionSuperType() }
        } ?: return
        if (tryExpression.containsMoreSpecificCatchClause(caughtGenericThrowableParam)) {
          // Cancellation exception will be caught by catch clause with a more specific type, so do not report
          return
        }

        // A language-specific check (e.g. Kotlin '@Throws') takes precedence; the UAST check is the fallback.
        val caughtCeInfo = findLangSpecificCeThrowingExpressionInfo(tryExpression, caughtGenericThrowableParam)
                           ?: findCeThrowingExpressionInfo(tryExpression, caughtGenericThrowableParam)
                           ?: return
        inspectIncorrectImplicitCeHandling(catchClause, caughtCeInfo)
        inspectImplicitCeLogging(catchClause, caughtCeInfo)
      }

      private fun UTryExpression.checkContainsSuspiciousCeCatchClause(): Boolean {
        val sourcePsi = this.sourcePsi ?: return false
        return cancellationExceptionHandlingChecker(this.lang)?.containsSuspiciousCeCatchClause(sourcePsi) == true
      }

      private fun findLangSpecificCeThrowingExpressionInfo(
        tryExpression: UTryExpression,
        caughtGenericThrowableParam: UParameter,
      ): CaughtCeInfo? {
        val ceHandlingChecker = cancellationExceptionHandlingChecker(tryExpression.lang)
        val ceThrowingExpressionName = ceHandlingChecker?.findCeThrowingExpressionName(tryExpression.sourcePsi!!) ?: return null
        return CaughtCeInfo(ceHandlingChecker.getCeName(), caughtGenericThrowableParam, ceThrowingExpressionName)
      }

      /**
       * Cancellation source of the `try` block: the first cancellation check, or the first directly called method that
       * declares a `ProcessCanceledException`.
       *
       * Lambdas and local or anonymous classes are skipped: the `try` block may only pass them on, for example to an
       * executor, so their calls do not run inside it. A Kotlin inline lambda that does run in place is skipped as well.
       */
      private fun findCeThrowingExpressionInfo(tryExpression: UTryExpression, caughtGenericThrowableParam: UParameter): CaughtCeInfo? {
        var caughtCeInfo: CaughtCeInfo? = null
        tryExpression.tryClause.accept(object : AbstractUastVisitor() {
          override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            return true
          }

          override fun visitClass(node: UClass): Boolean {
            return true
          }

          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (caughtCeInfo == null) {
              val thrownClassName = findThrownCancellationClassName(node, tryExpression)
              if (thrownClassName != null) {
                caughtCeInfo = CaughtCeInfo(thrownClassName, caughtGenericThrowableParam, node.methodName)
              }
            }
            return super.visitCallExpression(node)
          }
        })
        return caughtCeInfo
      }

      /**
       * Qualified name of the cancellation exception that [call] throws out of [tryExpression], or null when [call] is
       * neither a cancellation check nor a method that declares a `ProcessCanceledException` it can throw past a nested
       * `try`.
       */
      private fun findThrownCancellationClassName(call: UCallExpression, tryExpression: UTryExpression): String? {
        val checkedClassName = cancellationCheckClassName(call)
        if (checkedClassName != null) {
          return checkedClassName
        }
        val calledMethod = call.resolveToUElementOfType<UMethod>() ?: return null
        val throwsType = calledMethod.javaPsi.throwsTypes.filterIsInstance<PsiClassType>().firstOrNull { throwsType ->
          throwsType.isProcessCanceledExceptionType() && !isInNestedTryCatchBlock(call, tryExpression, throwsType)
        }
        return throwsType?.resolve()?.qualifiedName
      }

      private fun isInNestedTryCatchBlock(
        expression: UCallExpression,
        checkedTryExpression: UTryExpression,
        thrownExceptionType: PsiClassType,
      ): Boolean {
        val thrownExceptionClass = thrownExceptionType.resolve() ?: return false
        val parentTryExpression = expression.getParentOfType<UTryExpression>() ?: return false
        if (parentTryExpression == checkedTryExpression) return false
        return parentTryExpression.catchClauses.any { catchClause ->
          catchClause.types.any anyType@{ type ->
            return@anyType type.expandDisjunctions().any { it.isInheritorOrSelf(thrownExceptionClass) }
          }
        }
      }

      private fun inspectImplicitCeLogging(catchClause: UCatchClause, caughtCeInfo: CaughtCeInfo) {
        val methodName = caughtCeInfo.ceThrowingMethodName ?: return

        catchClause.body.accept(object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.receiverType?.isClassType(LOGGER_CLASS_NAME) != true) {
              return super.visitCallExpression(node)
            }

            if (ceIsRethrown(catchClause.body, caughtCeInfo.parameter, node)) {
              return super.visitCallExpression(node)
            }

            for (arg in node.valueArguments) {
              val resolvedParam = (arg as? USimpleNameReferenceExpression)?.resolveToUElement()
              if (resolvedParam == caughtCeInfo.parameter) {
                holder.registerUProblem(node as UExpression,
                                        message("inspections.incorrect.implicit.cancellation.exception.handling.name.logged",
                                                caughtCeInfo.ceClassName, methodName),
                                        // The broad catch also logs failures that are not cancellations, so the log stays;
                                        // a leading helper call rethrows the cancellation before it is logged.
                                        *insertHelperFixes(caughtCeInfo.parameter))
                return super.visitCallExpression(node)
              }
            }
            return super.visitCallExpression(node)
          }
        })
      }

      private fun inspectIncorrectImplicitCeHandling(catchClause: UCatchClause, caughtCeInfo: CaughtCeInfo) {
        if (!ceIsRethrown(catchClause.body, caughtCeInfo.parameter)) {
          val methodName = caughtCeInfo.ceThrowingMethodName ?: return
          holder.registerUProblem(caughtCeInfo.parameter,
                                  message("inspections.incorrect.implicit.cancellation.exception.handling.name.not.rethrown",
                                          caughtCeInfo.ceClassName, methodName),
                                  *insertHelperFixes(caughtCeInfo.parameter))
        }
      }

      // it checks only for rethrow-expression existence, without analyzing of all possible branches
      private fun ceIsRethrown(catchBody: UExpression, caughtParam: UParameter, logStatement: UElement? = null): Boolean {
        // 'e' (a direct reference to the caught parameter). Cause-based forms such as 'e.cause' are intentionally NOT
        // accepted: they rethrow the cause (or nothing when it is null), not 'e', so they can swallow the caught
        // cancellation.
        fun UExpression?.isCaughtParamRef(): Boolean {
          return this?.skipParenthesizedExprDown().asSafely<USimpleNameReferenceExpression>()?.resolveToUElement() == caughtParam
        }

        var found = false
        catchBody.accept(object : AbstractUastVisitor() {
          override fun visitThrowExpression(node: UThrowExpression): Boolean {
            if (logStatement == null) { // throw is mostly last statement
              val thrown = node.thrownExpression.skipParenthesizedExprDown()
              // 'throw e', or a wrap-and-rethrow such as 'throw CeProcessCanceledException(e)' that keeps the caught
              // control-flow exception in the cause chain.
              if (thrown.isCaughtParamRef() ||
                  (thrown is UCallExpression && thrown.returnType?.isControlFlowExceptionType() == true &&
                   thrown.valueArguments.any { it.isCaughtParamRef() })) {
                found = true
              }
            }
            return super.visitThrowExpression(node)
          }

          override fun visitCallExpression(node: UCallExpression): Boolean {
            // 'ensureActive()' / 'checkCanceled()' rethrow the current computation's cancellation, as the helper does.
            val rethrows = (node.methodName == RETHROW_FUN && node.valueArguments.firstOrNull().isCaughtParamRef()) ||
                           cancellationCheckClassName(node) != null
            if (rethrows) {
              if (logStatement == null) {
                found = true
              }
              else {
                val loggedAt = logStatement.sourcePsi?.startOffset
                val rethrownAt = node.sourcePsi?.startOffset
                if (loggedAt != null && rethrownAt != null && rethrownAt < loggedAt) {
                  found = true
                }
              }
            }
            return super.visitCallExpression(node)
          }
        })
        return found
      }

      private fun PsiType.isClassType(qualifiedName: String): Boolean {
        return expandDisjunctions().any { PsiTypesUtil.classNameEquals(it, qualifiedName) }
      }

      // An explicit 'catch' of 'ProcessCanceledException' (or an inheritor) intercepts the cancellation before a broad
      // 'catch', so the broad 'catch' is not to blame.
      private fun UTryExpression.containsInterceptingCatchClause(): Boolean {
        return catchClauses.any { clause ->
          clause.parameters.any { param ->
            param.type.expandDisjunctions().any { it.isProcessCanceledExceptionType() }
          }
        }
      }

      private fun UTryExpression.containsMoreSpecificCatchClause(param: UParameter): Boolean {
        val parameterQualifiedName = (param.type as? PsiClassType)?.resolve()?.qualifiedName ?: return false
        return catchClauses.any { clause ->
          clause.parameters.any { p ->
            p.type.expandDisjunctions().any { candidate ->
              // A narrower 'catch' that still intercepts the cancellation exception takes precedence over the broad one.
              candidate.isProcessCanceledExceptionSuperType() && candidate.isStrictSubtypeOfNamed(parameterQualifiedName)
            }
          }
        }
      }

      private fun PsiType.isStrictSubtypeOfNamed(qualifiedName: String): Boolean {
        val psiClass = (this as? PsiClassType)?.resolve() ?: return false
        return InheritanceUtil.isInheritor(psiClass, true, qualifiedName)
      }

    }, arrayOf(UCatchClause::class.java, UCallExpression::class.java))
  }

  private class CaughtCeInfo(
    val ceClassName: String,
    val parameter: UParameter,
    val ceThrowingMethodName: String? = null,
  )
}

private const val CHECK_CANCELED_METHOD = "checkCanceled"
private const val ENSURE_ACTIVE_METHOD = "ensureActive"
private const val PROGRESS_PACKAGE = "com.intellij.openapi.progress"
private const val PROGRESS_INDICATOR_CLASS_NAME = "com.intellij.openapi.progress.ProgressIndicator"
private const val COROUTINES_PACKAGE = "kotlinx.coroutines"

/**
 * Qualified name of the cancellation exception that [call] throws when the current computation is canceled, or null when
 * [call] is not a cancellation check.
 *
 * A cancellation check is `checkCanceled` declared in the `com.intellij.openapi.progress` package or by a
 * `ProgressIndicator` inheritor, and `ensureActive` declared in `kotlinx.coroutines`. The call is resolved, so an
 * unrelated method with the same name, such as the JPS `CompileContext.checkCanceled()`, is not one.
 */
private fun cancellationCheckClassName(call: UCallExpression): String? {
  val methodName = call.methodName
  if (methodName != CHECK_CANCELED_METHOD && methodName != ENSURE_ACTIVE_METHOD) {
    return null
  }
  val containingClass = call.resolve()?.containingClass ?: return null
  val packageName = PsiUtil.getPackageName(containingClass)
  if (methodName == CHECK_CANCELED_METHOD) {
    val isCheck = packageName == PROGRESS_PACKAGE || InheritanceUtil.isInheritor(containingClass, PROGRESS_INDICATOR_CLASS_NAME)
    return if (isCheck) PROCESS_CANCELED_EXCEPTION_CLASS_NAME else null
  }
  return if (packageName == COROUTINES_PACKAGE) CANCELLATION_EXCEPTION_CLASS_NAME else null
}

// allow additional languages to contribute language-specific checks
// (used by Kotlin at least for suspending context cancellation handling):

private val EP_NAME: ExtensionPointName<CancellationCheckProvider> =
  ExtensionPointName.Companion.create("DevKit.lang.cancellationExceptionHandlingChecker")

private object CancellationExceptionHandlingCheckers :
  LanguageExtension<CancellationExceptionHandlingChecker>(EP_NAME.name)

private fun cancellationExceptionHandlingChecker(language: Language): CancellationExceptionHandlingChecker? {
  return CancellationExceptionHandlingCheckers.forLanguage(language)
}

private fun insertHelperFixes(parameter: UParameter): Array<LocalQuickFix> {
  val sourcePsi = parameter.sourcePsi ?: return LocalQuickFix.EMPTY_ARRAY
  if (!isRethrowHelperAvailable(sourcePsi)) {
    return LocalQuickFix.EMPTY_ARRAY
  }
  return cancellationExceptionHandlingFixes(parameter, CancellationExceptionHandlingFixProvider::getInsertRethrowControlFlowExceptionFix)
}

private fun removeLoggingFixes(node: UCallExpression): Array<LocalQuickFix> {
  return cancellationExceptionHandlingFixes(node, CancellationExceptionHandlingFixProvider::getRemoveLoggingStatementFix)
}

@ApiStatus.Internal
interface CancellationExceptionHandlingChecker {
  fun isSuspicious(catchParameter: PsiElement): Boolean
  fun getCeName(): String
  fun containsSuspiciousCeCatchClause(tryExpression: PsiElement): Boolean
  fun findCeThrowingExpressionName(tryExpression: PsiElement): String?
}
