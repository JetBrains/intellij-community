// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

// "CE" stands for "cancellation exception", without a specific type meaning
private val ceClassNames = listOf("com.intellij.openapi.progress.ProcessCanceledException")

internal class IncorrectProcessCanceledExceptionHandlingInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

      override fun visitCatchClause(node: UCatchClause): Boolean {
        val catchParameters = node.parameters
        val (caughtCeParam, isCeInheritorCaught) = findSuspiciousCeCaughtParam(catchParameters)
        if (caughtCeParam != null) {
          inspectIncorrectCeHandling(node, caughtCeParam, isCeInheritorCaught)
        }
        else {
          inspectGenericThrowableIfAnyOfTryStatementsThrowsCe(node, catchParameters)
        }
        return super.visitCatchClause(node)
      }

      private fun findSuspiciousCeCaughtParam(catchParameters: List<UParameter>): Pair<UParameter?, Boolean> {
        val ceClasses = findCeClasses(holder.file.resolveScope) ?: return Pair(null, false)
        for (catchParameter in catchParameters) {
          val type = catchParameter.type
          val caughtExceptionTypes = if (type is PsiDisjunctionType) type.disjunctions else listOf(type)
            for (caughtExceptionType in caughtExceptionTypes) {
              if (ceClasses.any { caughtExceptionType.isInheritorOrSelf(it) }) {
                return Pair(catchParameter, caughtExceptionType.isCancellationExceptionClass())
              }
            }
        }
        return Pair(null, false)
      }

      private fun findCeClasses(resolveScope: GlobalSearchScope): List<PsiClass>? {
        return ceClassNames
          .mapNotNull { JavaPsiFacade.getInstance(holder.project).findClass(it, resolveScope) }
          .takeIf { it.isNotEmpty() }
      }

      private fun PsiType.isCancellationExceptionClass(): Boolean {
        return ceClassNames.any { it != (this as? PsiClassType)?.resolve()?.qualifiedName }
      }

      private fun PsiType.isInheritorOrSelf(ceClass: PsiClass): Boolean {
        val psiClassType = this as? PsiClassType ?: return false
        val psiClass = psiClassType.resolve() ?: return false
        return InheritanceUtil.isInheritorOrSelf(psiClass, ceClass, true)
      }

      private fun inspectIncorrectCeHandling(node: UCatchClause, caughtParam: UParameter, isCeInheritorCaught: Boolean) {
        val catchBody = node.body
        if (!ceIsRethrown(catchBody, caughtParam)) {
          val message = if (isCeInheritorCaught)
            DevKitBundle.message("inspections.incorrect.process.canceled.exception.inheritor.handling.name.not.rethrown")
          else DevKitBundle.message("inspections.incorrect.process.canceled.exception.handling.name.not.rethrown")
          holder.registerUProblem(caughtParam, message)
        }
        val loggingExpression = findCeLoggingExpression(catchBody, caughtParam)
        if (loggingExpression != null) {
          val message = if (isCeInheritorCaught)
            DevKitBundle.message("inspections.incorrect.process.canceled.exception.inheritor.handling.name.logged")
          else DevKitBundle.message("inspections.incorrect.process.canceled.exception.handling.name.logged")
          holder.registerUProblem(loggingExpression, message)
        }
      }

      private fun inspectGenericThrowableIfAnyOfTryStatementsThrowsCe(
        catchClause: UCatchClause,
        catchParameters: List<UParameter>,
      ): Boolean {
        val tryExpression = catchClause.getParentOfType<UTryExpression>() ?: return super.visitCatchClause(catchClause)
        if (tryExpression.containsCatchClauseForType<ProcessCanceledException>()) {
          // Cancellation exception will be caught by the explicit catch clause
          return super.visitCatchClause(catchClause)
        }
        val caughtGenericThrowableParam = catchParameters.firstOrNull {
          it.type.isClassType<RuntimeException>() || it.type.isClassType<Exception>() || it.type.isClassType<Throwable>()
        }
        if (caughtGenericThrowableParam != null) {
          if (tryExpression.containsMoreSpecificCatchClause(caughtGenericThrowableParam)) {
            // Cancellation exception will be caught by catch clause with a more specific type, so do not report
            return super.visitCatchClause(catchClause)
          }
          val (ceThrowingExpression, isCeInheritorThrown) = findCeThrowingExpression(tryExpression)
          if (ceThrowingExpression != null) {
            inspectIncorrectImplicitCeHandling(catchClause, caughtGenericThrowableParam, isCeInheritorThrown, ceThrowingExpression)
          }
        }
        return super.visitCatchClause(catchClause)
      }

      private fun inspectIncorrectImplicitCeHandling(
        node: UCatchClause,
        caughtParam: UParameter,
        isCeInheritor: Boolean,
        ceThrowingExpression: UCallExpression,
      ) {
        val catchBody = node.body
        if (!ceIsRethrown(catchBody, caughtParam)) {
          val methodName = ceThrowingExpression.methodName ?: return
          val message = if (isCeInheritor)
            DevKitBundle.message("inspections.incorrect.implicit.process.canceled.exception.inheritor.handling.name.not.rethrown",
                                 methodName)
          else DevKitBundle.message("inspections.incorrect.implicit.process.canceled.exception.handling.name.not.rethrown", methodName)
          holder.registerUProblem(caughtParam, message)
        }
        val loggingExpression = findCeLoggingExpression(catchBody, caughtParam)
        if (loggingExpression != null) {
          val methodName = ceThrowingExpression.methodName ?: return
          val message = if (isCeInheritor)
            DevKitBundle.message("inspections.incorrect.implicit.process.canceled.exception.inheritor.handling.name.logged", methodName)
          else DevKitBundle.message("inspections.incorrect.implicit.process.canceled.exception.handling.name.logged", methodName)
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
              if (callExpression.receiverType?.isClassType<Logger>() == true) {
                loggingExpression = callExpression
              }
            }
            return super.visitSimpleNameReferenceExpression(node)
          }
        })
        return loggingExpression
      }

      // it searches only for explicit `throws` declarations in directly called methods.
      private fun findCeThrowingExpression(tryExpression: UTryExpression): Pair<UCallExpression?, Boolean> {
        val tryClause = tryExpression.tryClause
        var ceThrowingExpression: UCallExpression? = null
        var isCeInheritorCaught = false
        val resolveScope = tryClause.sourcePsi?.resolveScope ?: return Pair(null, false)
        val ceClasses = findCeClasses(resolveScope) ?: return Pair(null, false)
        tryClause.accept(object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (ceThrowingExpression == null) {
              val calledMethod = node.resolveToUElementOfType<UMethod>() ?: return super.visitCallExpression(node)
              for (throwsType in calledMethod.javaPsi.throwsTypes) {
                val psiClassType = throwsType as? PsiClassType ?: continue
                if (ceClasses.any { psiClassType.isInheritorOrSelf(it) }) {
                  if (!isInNestedTryCatchBlock(node, tryExpression, psiClassType)) {
                    ceThrowingExpression = node
                    isCeInheritorCaught = psiClassType.isCancellationExceptionClass()
                    return super.visitCallExpression(node)
                  }
                }
              }
            }
            return super.visitCallExpression(node)
          }
        })
        return Pair(ceThrowingExpression, isCeInheritorCaught)
      }

      private inline fun <reified T> PsiType.isClassType(): Boolean {
        if (this is PsiDisjunctionType) {
          return this.disjunctions.any { PsiTypesUtil.classNameEquals(it, T::class.java.name) }
        }
        return PsiTypesUtil.classNameEquals(this, T::class.java.name)
      }

      private inline fun <reified T> UTryExpression.containsCatchClauseForType(): Boolean {
        return this.catchClauses.any { clause -> clause.parameters.any { it.type.isClassType<T>() } }
      }

      private fun UTryExpression.containsMoreSpecificCatchClause(param: UParameter): Boolean {
        return when ((param.type as? PsiClassType)?.resolve()?.qualifiedName) {
          java.lang.Throwable::class.java.name ->
            this.containsCatchClauseForType<Exception>() || this.containsCatchClauseForType<RuntimeException>()
          java.lang.Exception::class.java.name -> this.containsCatchClauseForType<RuntimeException>()
          else -> false
        }
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
            if (type is PsiDisjunctionType) {
              return@anyType type.disjunctions.any { it.isInheritorOrSelf(thrownExceptionClass) }
            }
            return@anyType type.isInheritorOrSelf(thrownExceptionClass)
          }
        }
      }

    }, arrayOf(UCatchClause::class.java))
  }
}
