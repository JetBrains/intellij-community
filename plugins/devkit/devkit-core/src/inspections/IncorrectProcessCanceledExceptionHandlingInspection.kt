// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDisjunctionType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

class IncorrectProcessCanceledExceptionHandlingInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

      override fun visitCatchClause(node: UCatchClause): Boolean {
        val catchParameters = node.parameters
        val caughtPceParam = catchParameters.firstOrNull { it.type.isClassType<ProcessCanceledException>() }
        if (caughtPceParam != null) {
          inspectIncorrectPceHandling(node, caughtPceParam)
        }
        else {
          val tryExpression = node.getParentOfType<UTryExpression>() ?: return super.visitCatchClause(node)
          if (tryExpression.containsPceCatchClause()) {
            // PCE will be caught by the explicit catch clause
            return super.visitCatchClause(node)
          }
          val caughtGenericThrowableParam =
            catchParameters.firstOrNull {
              it.type.isClassType<Exception>() || it.type.isClassType<RuntimeException>() || it.type.isClassType<Throwable>()
            }
          if (caughtGenericThrowableParam != null) {
            val pceThrowingExpression = findPceThrowingExpression(tryExpression.tryClause)
            if (pceThrowingExpression != null) {
              inspectIncorrectImplicitPceHandling(node, caughtGenericThrowableParam, pceThrowingExpression)
            }
          }
        }
        return super.visitCatchClause(node)
      }

      private fun inspectIncorrectPceHandling(node: UCatchClause, caughtParam: UParameter) {
        val catchBody = node.body
        if (!pceIsRethrown(catchBody, caughtParam)) {
          holder.registerUProblem(caughtParam,
                                  DevKitBundle.message("inspections.incorrect.process.canceled.exception.handling.name.not.rethrown"))
        }
        val loggingExpression = findPceLoggingExpression(catchBody, caughtParam)
        if (loggingExpression != null) {
          holder.registerUProblem(loggingExpression,
                                  DevKitBundle.message("inspections.incorrect.process.canceled.exception.handling.name.logged"))
        }
      }

      private fun inspectIncorrectImplicitPceHandling(node: UCatchClause, caughtParam: UParameter, pceThrowingExpression: UCallExpression) {
        val catchBody = node.body
        if (!pceIsRethrown(catchBody, caughtParam)) {
          val methodName = pceThrowingExpression.methodName ?: return
          holder.registerUProblem(caughtParam, DevKitBundle.message(
            "inspections.incorrect.implicit.process.canceled.exception.handling.name.not.rethrown", methodName))
        }
        val loggingExpression = findPceLoggingExpression(catchBody, caughtParam)
        if (loggingExpression != null) {
          val methodName = pceThrowingExpression.methodName ?: return
          holder.registerUProblem(loggingExpression, DevKitBundle.message(
            "inspections.incorrect.implicit.process.canceled.exception.handling.name.logged", methodName))
        }
      }

      // it checks only for `throw exception` expression existence, without analyzing of all possible branches
      private fun pceIsRethrown(catchBody: UExpression, caughtParam: UParameter): Boolean {
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

      private fun findPceLoggingExpression(catchBody: UExpression, caughtParam: UParameter): UExpression? {
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
      private fun findPceThrowingExpression(tryClause: UExpression): UCallExpression? {
        var pceThrowingExpression: UCallExpression? = null
        tryClause.accept(object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val calledMethod = node.resolveToUElement() as? UMethod ?: return super.visitCallExpression(node)
            if (calledMethod.javaPsi.throwsTypes.any { (it.resolve() as? PsiClass)?.qualifiedName == ProcessCanceledException::class.java.name }) {
              pceThrowingExpression = node
            }
            return super.visitCallExpression(node)
          }
        })
        return pceThrowingExpression
      }

      private inline fun <reified T> PsiType.isClassType(): Boolean {
        if (this is PsiDisjunctionType) {
          return this.disjunctions.any { PsiTypesUtil.classNameEquals(it, T::class.java.name) }
        }
        return PsiTypesUtil.classNameEquals(this, T::class.java.name)
      }

      private fun UTryExpression.containsPceCatchClause(): Boolean {
        return this.catchClauses.any { clause -> clause.parameters.any { it.type.isClassType<ProcessCanceledException>() } }
      }

    }, arrayOf(UCatchClause::class.java))
  }
}
