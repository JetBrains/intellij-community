// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val PCE_CLASS_NAME = "com.intellij.openapi.progress.ProcessCanceledException"
private const val RUNTIME_EXCEPTION_CLASS_NAME = "java.lang.RuntimeException"
private const val EXCEPTION_CLASS_NAME = "java.lang.Exception"
private const val THROWABLE_CLASS_NAME = "java.lang.Throwable"

internal class IncorrectCancellationExceptionHandlingInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

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
        val pceClass = findPceClass(holder.file.resolveScope) ?: return null
        for (catchParameter in catchParameters) {
          // language-specific check:
          val checker = cancellationExceptionHandlingChecker(catchParameter.lang)
          val sourcePsi = catchParameter.sourcePsi
          if (sourcePsi != null && checker?.isSuspicious(sourcePsi) == true) {
            return CaughtCeInfo(checker.getCeName(), false, catchParameter)
          }
          // general UAST check:
          val type = catchParameter.type
          val caughtExceptionTypes = (if (type is PsiDisjunctionType) type.disjunctions else listOf(type)).filterIsInstance<PsiClassType>()
          for (caughtExceptionType in caughtExceptionTypes) {
            if (caughtExceptionType.isInheritorOrSelf(pceClass)) {
              return CaughtCeInfo(
                PCE_CLASS_NAME,
                !caughtExceptionType.isCancellationExceptionClass(),
                catchParameter
              )
            }
          }
        }
        return null
      }

      private fun findPceClass(resolveScope: GlobalSearchScope): PsiClass? {
        return JavaPsiFacade.getInstance(holder.project).findClass(PCE_CLASS_NAME, resolveScope)
      }

      private fun PsiType.isCancellationExceptionClass(): Boolean {
        return PCE_CLASS_NAME == (this as? PsiClassType)?.resolve()?.qualifiedName
      }

      private fun PsiType.isInheritorOrSelf(ceClass: PsiClass): Boolean {
        val psiClassType = this as? PsiClassType ?: return false
        val psiClass = psiClassType.resolve() ?: return false
        return InheritanceUtil.isInheritorOrSelf(psiClass, ceClass, true)
      }

      private fun inspectIncorrectCeHandling(node: UCatchClause, caughtCeInfo: CaughtCeInfo) {
        val catchBody = node.body
        if (!ceIsRethrown(catchBody, caughtCeInfo.parameter)) {
          val message = if (caughtCeInfo.isInheritor)
            message("inspections.incorrect.cancellation.exception.inheritor.handling.name.not.rethrown", caughtCeInfo.baseCeClassName)
          else message("inspections.incorrect.cancellation.exception.handling.name.not.rethrown", caughtCeInfo.baseCeClassName)
          holder.registerUProblem(caughtCeInfo.parameter, message)
        }
        val loggingExpression = findCeLoggingExpression(catchBody, caughtCeInfo.parameter)
        if (loggingExpression != null) {
          val message = if (caughtCeInfo.isInheritor)
            message("inspections.incorrect.cancellation.exception.inheritor.handling.name.logged", caughtCeInfo.baseCeClassName)
          else message("inspections.incorrect.cancellation.exception.handling.name.logged", caughtCeInfo.baseCeClassName)
          holder.registerUProblem(loggingExpression, message)
        }
      }

      private fun inspectGenericThrowableIfAnyOfTryStatementsThrowsCe(
        catchClause: UCatchClause,
        catchParameters: List<UParameter>,
      ): Boolean {
        val tryExpression = catchClause.getParentOfType<UTryExpression>() ?: return super.visitCatchClause(catchClause)
        if (tryExpression.containsCatchClauseForType(PCE_CLASS_NAME) || tryExpression.checkContainsSuspiciousCeCatchClause()) {
          // Cancellation exception will be caught by the explicit catch clause
          return super.visitCatchClause(catchClause)
        }
        val caughtGenericThrowableParam = catchParameters.firstOrNull {
          it.type.isClassType(RUNTIME_EXCEPTION_CLASS_NAME) ||
          it.type.isClassType(EXCEPTION_CLASS_NAME) ||
          it.type.isClassType(THROWABLE_CLASS_NAME)
        }
        if (caughtGenericThrowableParam != null) {
          if (tryExpression.containsMoreSpecificCatchClause(caughtGenericThrowableParam)) {
            // Cancellation exception will be caught by catch clause with a more specific type, so do not report
            return super.visitCatchClause(catchClause)
          }

          // lang-specific check:
          val langSpecificCaughtCeInfo = findLangSpecificCeThrowingExpressionInfo(tryExpression, caughtGenericThrowableParam)
          if (langSpecificCaughtCeInfo != null) {
            inspectIncorrectImplicitCeHandling(catchClause, langSpecificCaughtCeInfo)
          }
          // UAST check:
          val caughtCeInfo = findCeThrowingExpressionInfo(tryExpression, caughtGenericThrowableParam)
          if (caughtCeInfo != null) {
            inspectIncorrectImplicitCeHandling(catchClause, caughtCeInfo)
          }
        }
        return super.visitCatchClause(catchClause)
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
        return CaughtCeInfo(ceHandlingChecker.getCeName(), false, caughtGenericThrowableParam, ceThrowingExpressionName)
      }

      // it searches only for explicit `throws` declarations in directly called methods.
      private fun findCeThrowingExpressionInfo(tryExpression: UTryExpression, caughtGenericThrowableParam: UParameter): CaughtCeInfo? {
        var caughtCeInfo: CaughtCeInfo? = null
        val tryClause = tryExpression.tryClause
        val resolveScope = tryClause.sourcePsi?.resolveScope ?: return null
        val pceClass = findPceClass(resolveScope) ?: return null
        tryClause.accept(object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (caughtCeInfo == null) {
              val calledMethod = node.resolveToUElementOfType<UMethod>() ?: return super.visitCallExpression(node)
              for (throwsType in calledMethod.javaPsi.throwsTypes) {
                val throwsClassType = throwsType as? PsiClassType ?: continue
                if (throwsClassType.isInheritorOrSelf(pceClass)) {
                  if (!isInNestedTryCatchBlock(node, tryExpression, throwsClassType)) {
                    caughtCeInfo = CaughtCeInfo(
                      PCE_CLASS_NAME,
                      !throwsClassType.isCancellationExceptionClass(),
                      caughtGenericThrowableParam,
                      node.methodName
                    )
                    return super.visitCallExpression(node)
                  }
                }
              }
            }
            return super.visitCallExpression(node)
          }
        })
        return caughtCeInfo
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
            return@anyType (if (type is PsiDisjunctionType) type.disjunctions else listOf(type))
              .any { it.isInheritorOrSelf(thrownExceptionClass) }
          }
        }
      }

      private fun inspectIncorrectImplicitCeHandling(catchClause: UCatchClause, caughtCeInfo: CaughtCeInfo) {
        val catchBody = catchClause.body
        if (!ceIsRethrown(catchBody, caughtCeInfo.parameter)) {
          val methodName = caughtCeInfo.ceThrowingMethodName ?: return
          val message = if (caughtCeInfo.isInheritor)
            message("inspections.incorrect.implicit.cancellation.exception.inheritor.handling.name.not.rethrown",
                    caughtCeInfo.baseCeClassName, methodName)
          else message("inspections.incorrect.implicit.cancellation.exception.handling.name.not.rethrown",
                       caughtCeInfo.baseCeClassName, methodName)
          holder.registerUProblem(caughtCeInfo.parameter, message)
        }
        val loggingExpression = findCeLoggingExpression(catchBody, caughtCeInfo.parameter)
        if (loggingExpression != null) {
          val methodName = caughtCeInfo.ceThrowingMethodName ?: return
          val message = if (caughtCeInfo.isInheritor)
            message("inspections.incorrect.implicit.cancellation.exception.inheritor.handling.name.logged",
                    caughtCeInfo.baseCeClassName, methodName)
          else message("inspections.incorrect.implicit.cancellation.exception.handling.name.logged",
                       caughtCeInfo.baseCeClassName, methodName)
          holder.registerUProblem(loggingExpression, message)
        }
      }

      // it checks only for `throw exception` expression existence, without analyzing of all possible branches
      private fun ceIsRethrown(catchBody: UExpression, caughtParam: UParameter): Boolean {
        var found = false
        catchBody.accept(object : AbstractUastVisitor() {
          override fun visitThrowExpression(node: UThrowExpression): Boolean {
            val resolvedParam = (node.thrownExpression as? USimpleNameReferenceExpression)?.resolveToUElement()
            if (caughtParam == resolvedParam) {
              found = true
            }
            return super.visitThrowExpression(node)
          }
        })
        return found
      }

      private fun findCeLoggingExpression(catchBody: UExpression, caughtParam: UParameter): UExpression? {
        var loggingExpression: UExpression? = null
        catchBody.accept(object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            if (caughtParam == node.resolveToUElement()) {
              val callExpression = node.getParentOfType<UCallExpression>() ?: return super.visitSimpleNameReferenceExpression(node)
              if (callExpression.receiverType?.isClassType("com.intellij.openapi.diagnostic.Logger") == true) {
                loggingExpression = callExpression
              }
            }
            return super.visitSimpleNameReferenceExpression(node)
          }
        })
        return loggingExpression
      }

      private fun PsiType.isClassType(fullyQualifiedClassName: String): Boolean {
        if (this is PsiDisjunctionType) {
          return this.disjunctions.any { PsiTypesUtil.classNameEquals(it, fullyQualifiedClassName) }
        }
        return PsiTypesUtil.classNameEquals(this, fullyQualifiedClassName)
      }

      private fun UTryExpression.containsCatchClauseForType(fullyQualifiedClassName: String): Boolean {
        return this.catchClauses.any { clause -> clause.parameters.any { it.type.isClassType(fullyQualifiedClassName) } }
      }

      private fun UTryExpression.containsMoreSpecificCatchClause(param: UParameter): Boolean {
        return when ((param.type as? PsiClassType)?.resolve()?.qualifiedName) {
          java.lang.Throwable::class.java.name ->
            this.containsCatchClauseForType(EXCEPTION_CLASS_NAME) || this.containsCatchClauseForType(RUNTIME_EXCEPTION_CLASS_NAME)
          java.lang.Exception::class.java.name -> this.containsCatchClauseForType(RUNTIME_EXCEPTION_CLASS_NAME)
          else -> false
        }
      }

    }, arrayOf(UCatchClause::class.java))
  }

  private class CaughtCeInfo(
    val baseCeClassName: String,
    val isInheritor: Boolean,
    val parameter: UParameter,
    val ceThrowingMethodName: String? = null,
  )
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

@IntellijInternalApi
@ApiStatus.Internal
interface CancellationExceptionHandlingChecker {
  fun isSuspicious(catchParameter: PsiElement): Boolean
  fun getCeName(): String
  fun containsSuspiciousCeCatchClause(tryExpression: PsiElement): Boolean
  fun findCeThrowingExpressionName(tryExpression: PsiElement): String?
}
