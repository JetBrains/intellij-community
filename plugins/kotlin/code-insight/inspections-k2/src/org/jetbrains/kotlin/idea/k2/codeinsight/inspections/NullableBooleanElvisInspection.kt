// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

/**
 * For an elvis operation, this class detects and replaces it with a boolean equality check:
 *   - `nb ?: true` => `nb != false`
 *   - `nb ?: false` => `nb == true`
 *   - `!(nb ?: true)` => `nb == false`
 *   - `!(nb ?: false)` => `nb != true`
 * See plugins/kotlin/code-insight/descriptions/resources-en/inspectionDescriptions/NullableBooleanElvis.html for details.
 */
internal class NullableBooleanElvisInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: Unit,
    ): String = KotlinBundle.message("inspection.nullable.boolean.elvis.display.name")

    // According to the Kotlin style guide, elvis should not be used in conditional statements, so we leave it as a warning.
    // All other cases have no highlighting.
    override fun getProblemHighlightType(element: KtBinaryExpression, context: Unit): ProblemHighlightType {
        // Don't highlight compound elvis expressions like `a ?: b ?: false` for readability
        (element.left as? KtBinaryExpression)?.let { leftBinary ->
            if (leftBinary.operationToken == KtTokens.ELVIS) return INFORMATION
        }

        val parentIfOrWhile = PsiTreeUtil.getParentOfType(element, KtIfExpression::class.java, KtWhileExpressionBase::class.java)
        val condition = when (parentIfOrWhile) {
            is KtIfExpression -> parentIfOrWhile.condition
            is KtWhileExpressionBase -> parentIfOrWhile.condition
            else -> PsiTreeUtil.getParentOfType(element, KtWhenCondition::class.java)
        }

        return if (condition != null && condition in element.parentsWithSelf) {
            GENERIC_ERROR_OR_WARNING
        } else {
            INFORMATION
        }
    }

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> =
        listOf(element.operationReference.textRangeInParent)

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean = element.isTargetOfNullableBooleanElvisInspection()

    context(KaSession)
    override fun prepareContext(element: KtBinaryExpression): Unit? {
        return element.left
            ?.expressionType
            ?.let { it.isBooleanType && it.nullability.isNullable }
            ?.asUnit
    }

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Unit,
    ) = object : KotlinModCommandQuickFix<KtBinaryExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.nullable.boolean.elvis.action.name")

        override fun applyFix(
            project: Project,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            val lhs = element.left ?: return
            val rhs = element.right as? KtConstantExpression ?: return
            val parentWithNegation = element.parentThroughParenthesisWithNegation()
            if (parentWithNegation == null) {
                element.replaceElvisWithBooleanEqualityOperation(lhs, rhs)
            } else {
                parentWithNegation.replaceElvisWithBooleanEqualityOperation(lhs, rhs, hasNegation = true)
            }
        }
    }

    /**
     * To be a target of the nullable boolean elvis inspection,
     *  - The binary operator must be elvis.
     *  - RHS must be a boolean constant.
     *  - LHS must have a nullable boolean type. - This is checked by `isApplicableByAnalyze` above.
     */
    private fun KtBinaryExpression.isTargetOfNullableBooleanElvisInspection(): Boolean =
        operationToken == KtTokens.ELVIS && right?.let { KtPsiUtil.isBooleanConstant(it) } == true

    /**
     * A method to find the nearest recursive parent with a negation operator.
     *
     * This method recursively visits parent of the binary expression when the parent is KtParenthesizedExpression.
     * Finally, when it finds the nearest recursive parent with a negation operator, it returns the parent.
     * If there is no such parent, it returns null.
     */
    private fun KtBinaryExpression.parentThroughParenthesisWithNegation(): KtUnaryExpression? {
        var result = parent
        while (result is KtParenthesizedExpression) result = result.parent
        val unaryExpressionParent = result as? KtUnaryExpression ?: return null
        return if (unaryExpressionParent.operationToken == KtTokens.EXCL) unaryExpressionParent else null
    }

    /**
     * This method replaces "lhs ?: rhs" with "lhs == true" or "lhs != false" depending on "rhs".
     */
    private fun KtExpression.replaceElvisWithBooleanEqualityOperation(
        lhs: KtExpression,
        rhs: KtConstantExpression,
        hasNegation: Boolean = false
    ) {
        val isFalse = KtPsiUtil.isFalseConstant(rhs)
        val constantToCompare = if (isFalse) "true" else "false"
        val operator = if (isFalse xor hasNegation) "==" else "!="
        replaced(KtPsiFactory(project).buildExpression {
            appendExpression(lhs)
            appendFixedText(" $operator $constantToCompare")
        })
    }
}