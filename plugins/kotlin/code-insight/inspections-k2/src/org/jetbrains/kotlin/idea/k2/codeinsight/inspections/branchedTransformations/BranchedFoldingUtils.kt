/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.branchedTransformations

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

object BranchedFoldingUtils {
    private val KtIfExpression.branches: List<KtExpression?> get() = ifBranchesOrThis()

    private fun KtExpression.ifBranchesOrThis(): List<KtExpression?> {
        if (this !is KtIfExpression) return listOf(this)
        return listOf(then) + `else`?.ifBranchesOrThis().orEmpty()
    }

    private fun KtTryExpression.tryBlockAndCatchBodies(): List<KtExpression?> = listOf(tryBlock) + catchClauses.map { it.catchBody }

    /**
     * Returns the last statement from the block of [branch] only if the statement is an assignment.
     */
    private fun getFoldableBranchedAssignment(branch: KtExpression?): KtBinaryExpression? {
        fun checkAssignment(expression: KtBinaryExpression): Boolean {
            if (expression.operationToken !in KtTokens.ALL_ASSIGNMENTS) return false

            val left = expression.left as? KtNameReferenceExpression ?: return false
            if (expression.right == null) return false

            val parent = expression.parent
            if (parent is KtBlockExpression) {
                return !KtPsiUtil.checkVariableDeclarationInBlock(parent, left.text)
            }

            return true
        }
        return (branch?.lastBlockStatementOrThis() as? KtBinaryExpression)?.takeIf(::checkAssignment)
    }

    /**
     * A function to lift the common left expression from assignment expressions in branches of if, when, or try [expression].
     *
     * Steps of lifting the common left expression:
     *   1. From if, when, or try [expression], visit each branch to collect all assignment expressions. If [expression] is not if, when,
     *      or try expression, this function just returns. (See [lift] function below)
     *   2. Uses [KtPsiFactory] to create a stand-alone expression that is the same as each right expression of collected assignments, and
     *      replaces each assignment expression with the created stand-alone expression.
     *      (See [KtBinaryExpression.replaceWithRHS] function below)
     *   3. Replaces [expression] with a new expression in a form of
     *      `(the common left expression from assignment expressions) ASSIGNMENT_OPERATION (branches with right expression)`
     */
    fun tryFoldToAssignment(expression: KtExpression) {
        var lhs: KtExpression? = null
        var op: String? = null
        val psiFactory = KtPsiFactory(expression)
        fun KtBinaryExpression.replaceWithRHS() {
            if (lhs == null || op == null) {
                lhs = left!!.copy() as KtExpression
                op = operationReference.text
            }

            val rhs = right!!
            if (rhs is KtLambdaExpression && this.parent !is KtBlockExpression) {
                replace(psiFactory.createSingleStatementBlock(rhs))
            } else {
                replace(rhs)
            }
        }

        fun lift(e: KtExpression?) {
            when (e) {
                is KtWhenExpression -> e.entries.forEach { entry ->
                    getFoldableBranchedAssignment(entry.expression)?.replaceWithRHS() ?: lift(entry.expression?.lastBlockStatementOrThis())
                }

                is KtIfExpression -> e.branches.forEach { branch ->
                    getFoldableBranchedAssignment(branch)?.replaceWithRHS() ?: lift(branch?.lastBlockStatementOrThis())
                }

                is KtTryExpression -> e.tryBlockAndCatchBodies().forEach {
                    getFoldableBranchedAssignment(it)?.replaceWithRHS() ?: lift(it?.lastBlockStatementOrThis())
                }
            }
        }
        lift(expression)
        if (lhs != null && op != null) {
            expression.replace(psiFactory.createExpressionByPattern("$0 $1 $2", lhs!!, op!!, expression))
        }
    }

    /**
     * Returns the number of assignments in recursive branches of if, when, or try expression (it checks nested branches as well).
     *
     * @param expression if, when, or try expression to analyze branches for the number of assignments.
     * @return null if
     *   - [expression] is null,
     *   - [expression] has some missing cases (cases that no branches handle), or
     *   - The right expressions of assignments do not match each other
     *     (for the concept of "match", see [collectAssignmentsAndCheck] function below).
     *   Otherwise, the number of all assignments.
     */
    fun KtAnalysisSession.getNumberOfFoldableAssignmentsOrNull(expression: KtExpression?): Int? {
        if (expression == null) return null
        val assignments = mutableSetOf<KtBinaryExpression>()

        /**
         * A function to collect assignments in branches of if, when, or try expression when it does not have missing cases and
         * right expressions of all assignments match.
         *
         * We say "two right expressions match" only when the operator functions for the two assignments match.
         * When there are nested if/when/try expressions in branches, it recursively collects the assignments as well.
         * @param e the if, when, or try expression.
         * @return true if it does not have any missing cases. Otherwise, false.
         */
        fun collectAssignmentsAndCheck(e: KtExpression?): Boolean = when (e) {
            is KtWhenExpression -> {
                val entries = e.entries
                // When the KtWhenExpression has missing cases with an else branch, we cannot fold it.
                if (!KtPsiUtil.checkWhenExpressionHasSingleElse(e) && e.getMissingCases().isNotEmpty()) false
                else entries.isNotEmpty() && entries.all { entry ->
                    val assignment = getFoldableBranchedAssignment(entry.expression)?.run { assignments.add(this) }
                    assignment != null || collectAssignmentsAndCheck(entry.expression?.lastBlockStatementOrThis())
                }
            }

            is KtIfExpression -> {
                val branches = e.branches
                val elseBranch = branches.lastOrNull()?.getStrictParentOfType<KtIfExpression>()?.`else`
                branches.size > 1 && elseBranch != null && branches.all { branch ->
                    val assignment = getFoldableBranchedAssignment(branch)?.run { assignments.add(this) }
                    assignment != null || collectAssignmentsAndCheck(branch?.lastBlockStatementOrThis())
                }
            }

            is KtTryExpression -> {
                e.tryBlockAndCatchBodies().all {
                    val assignment = getFoldableBranchedAssignment(it)?.run { assignments.add(this) }
                    assignment != null || collectAssignmentsAndCheck(it?.lastBlockStatementOrThis())
                }
            }

            is KtCallExpression -> {
                e.getKtType()?.isNothing ?: false
            }

            is KtBreakExpression, is KtContinueExpression, is KtThrowExpression, is KtReturnExpression -> true

            else -> false
        }

        // Check if all assignment have right expressions that match each other.
        if (!collectAssignmentsAndCheck(expression)) return null
        val firstAssignment = assignments.firstOrNull { it.right?.isNull() != true } ?: assignments.firstOrNull() ?: return 0
        val leftType = firstAssignment.left?.getKtType() ?: return 0
        val rightType = firstAssignment.right?.getKtType() ?: return 0
        val firstOperation = firstAssignment.operationReference.mainReference.resolve()
        if (assignments.any { assignment -> !checkAssignmentsMatch(firstAssignment, assignment, firstOperation, leftType, rightType) }) {
            return null
        }

        // If there is any child element of expression whose type is KtBinaryExpression for an assignment that does not have the right
        // expression matching the right expression of the firstAssignment, returns -1.
        if (expression.anyDescendantOfType<KtBinaryExpression>(predicate = { binaryExpression ->
                if (binaryExpression.operationToken !in KtTokens.ALL_ASSIGNMENTS) return@anyDescendantOfType false

                if (binaryExpression.getNonStrictParentOfType<KtFinallySection>() != null) {
                    checkAssignmentsMatch(firstAssignment, binaryExpression, firstOperation, leftType, rightType)
                } else {
                    binaryExpression !in assignments
                }
            })) {
            return null
        }
        return assignments.size
    }

    /**
     * Returns whether the binary assignment expressions [first] and [second] match or not.
     * We say they match when they satisfy the following conditions:
     *  1. They have the same left operands
     *  2. They have the same operation tokens
     *  3. It satisfies one of the following:
     *      - The left operand is nullable and the right is null
     *      - Their operations are the same
     *      - If we cannot find symbols of their operations
     *          - When [leftType] is a nullable type, types of right operands of [first] and [second] with non-nullability are the same
     *          - When [leftType] is a non-nullable type, types of right operands of [first] and [second] are both non-nullable, and
     *            they are the same.
     */
    private fun KtAnalysisSession.checkAssignmentsMatch(
        first: KtBinaryExpression,
        second: KtBinaryExpression,
        firstOperation: PsiElement?,
        leftType: KtType,
        nonNullableRightTypeOfFirst: KtType,
    ): Boolean {
        // Check if they satisfy the above condition 1 and 2.
        fun haveSameLeft(first: KtBinaryExpression, second: KtBinaryExpression): Boolean {
            val leftOfFirst = first.left ?: return false
            val leftOfSecond = second.left ?: return false
            val leftSymbolOfFirst = leftOfFirst.mainReference?.resolve() ?: return false
            val leftSymbolOfSecond = leftOfSecond.mainReference?.resolve() ?: return false
            return leftSymbolOfFirst == leftSymbolOfSecond
        }
        if (!haveSameLeft(first, second) || first.operationToken != second.operationToken) return false

        // Check if they satisfy the first of condition 3.
        val isSecondRightNull = second.right?.isNull()
        if (isSecondRightNull == true && leftType.canBeNull) return true

        // Check if they satisfy the second of condition 3.
        if (firstOperation != null && firstOperation == second.operationReference.mainReference.resolve()) return true

        // Check if they satisfy the third and fourth of condition 3.
        val rightTypeOfSecond = second.right?.getKtType() ?: return false
        if (!leftType.canBeNull && rightTypeOfSecond.canBeNull) return false
        val nonNullableRightTypeOfSecond = rightTypeOfSecond.withNullability(KtTypeNullability.NON_NULLABLE)
        return nonNullableRightTypeOfFirst isEqualTo nonNullableRightTypeOfSecond ||
                (first.operationToken == KtTokens.EQ && nonNullableRightTypeOfSecond isSubTypeOf leftType)
    }
}