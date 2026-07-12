// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter.Companion.create
import org.jetbrains.idea.devkit.DevKitBundle.message
import org.jetbrains.idea.devkit.inspections.quickfix.CONTROL_FLOW_EXCEPTIONS_FACADE_FQN
import org.jetbrains.idea.devkit.inspections.quickfix.CancellationExceptionHandlingFixProvider
import org.jetbrains.idea.devkit.inspections.quickfix.cancellationExceptionHandlingFixes
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.util.isInstanceCheck
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

private const val LOGGER_CLASS_NAME = "com.intellij.openapi.diagnostic.Logger"
private const val SHOULD_RETHROW_METHOD = "shouldRethrow"

/**
 * Kind name of the Kotlin `!is` check, a distinct `InstanceCheck` in UAST.
 *
 * The kind is matched by name so that DevKit does not depend on the `@ApiStatus.Internal`
 * `KotlinBinaryExpressionWithTypeKinds.NEGATED_INSTANCE_CHECK` of the Kotlin UAST module.
 */
private const val NEGATED_INSTANCE_CHECK_NAME = "!is"

/**
 * Reports a hand-rolled `if (e is ControlFlowException) throw e` rethrow guard and offers to replace it with the
 * `rethrowControlFlowException()` helper, which recognizes the full control-flow set at once.
 */
internal class ControlFlowExceptionManualRethrowInspection : DevKitUastInspectionBase() {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return super.isAllowed(holder) && DevKitInspectionUtil.isClassAvailable(holder, CONTROL_FLOW_EXCEPTIONS_FACADE_FQN)
  }

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

      override fun visitIfExpression(node: UIfExpression): Boolean {
        val elseExpression = node.elseExpression
        if (elseExpression != null && elseExpression !is UastEmptyExpression) {
          return super.visitIfExpression(node)
        }
        val thrownVariable = singleThrownVariable(node.thenExpression) ?: return super.visitIfExpression(node)
        if (isControlFlowRethrowGuard(node.condition, thrownVariable)) {
          val fixes = cancellationExceptionHandlingFixes(node, CancellationExceptionHandlingFixProvider::getReplaceGuardWithRethrowHelperFix)
          holder.registerUProblem(node as UExpression, message("inspections.control.flow.exception.manual.rethrow.name"), *fixes)
        }
        return super.visitIfExpression(node)
      }

      private fun singleThrownVariable(thenExpression: UExpression?): PsiElement? {
        val throwExpression = when (thenExpression) {
          is UThrowExpression -> thenExpression
          is UBlockExpression -> thenExpression.expressions.singleOrNull() as? UThrowExpression
          else -> null
        } ?: return null
        val thrown = throwExpression.thrownExpression.skipParenthesizedExprDown() as? USimpleNameReferenceExpression ?: return null
        return thrown.resolve()
      }

      private fun isControlFlowRethrowGuard(condition: UExpression, thrownVariable: PsiElement): Boolean {
        val unwrapped = condition.skipParenthesizedExprDown()
        val call = unwrapped as? UCallExpression ?: (unwrapped as? UQualifiedReferenceExpression)?.selector as? UCallExpression
        if (call != null) {
          return isShouldRethrowCall(call, thrownVariable)
        }
        return when (unwrapped) {
          is UBinaryExpressionWithType -> isControlFlowInstanceCheck(unwrapped, thrownVariable)
          is UPolyadicExpression -> unwrapped.operator == UastBinaryOperator.LOGICAL_OR &&
                                    unwrapped.operands.isNotEmpty() &&
                                    unwrapped.operands.all {
                                      val operand = it.skipParenthesizedExprDown()
                                      operand is UBinaryExpressionWithType && isControlFlowInstanceCheck(operand, thrownVariable)
                                    }
          else -> false
        }
      }

      private fun isControlFlowInstanceCheck(expression: UBinaryExpressionWithType, thrownVariable: PsiElement): Boolean {
        // Positive 'instanceof' / 'is' only; the negated 'is' guard rethrows the wrong branch, so it is skipped.
        if (!expression.isInstanceCheck() || expression.operationKind.name == NEGATED_INSTANCE_CHECK_NAME) return false
        val operand = expression.operand.skipParenthesizedExprDown() as? USimpleNameReferenceExpression ?: return false
        return operand.resolve() == thrownVariable && expression.type.isControlFlowExceptionType()
      }

      private fun isShouldRethrowCall(call: UCallExpression, thrownVariable: PsiElement): Boolean {
        if (call.methodName != SHOULD_RETHROW_METHOD) return false
        val method = call.resolve() ?: return false
        if (method.containingClass?.qualifiedName != LOGGER_CLASS_NAME) return false
        val argument = call.valueArguments.singleOrNull()?.skipParenthesizedExprDown() as? USimpleNameReferenceExpression ?: return false
        return argument.resolve() == thrownVariable
      }

    }, arrayOf(UIfExpression::class.java))
  }
}
