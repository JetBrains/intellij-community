// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * A class for nullable boolean elvis inspection.
 *
 * For an elvis operation, this class detects and replaces it with a boolean equality check:
 *   - `nb ?: true` => `nb != false`
 *   - `nb ?: false` => `nb == true`
 *   - `!(nb ?: true)` => `nb == false`
 *   - `!(nb ?: false)` => `nb != true`
 * See plugins/kotlin/code-insight/descriptions/resources-en/inspectionDescriptions/NullableBooleanElvis.html for details.
 */
class NullableBooleanElvisInspection :
    AbstractKotlinApplicatorBasedInspection<KtBinaryExpression, NullableBooleanElvisInspection.NullableBooleanInput>(
        KtBinaryExpression::class
    ) {
    class NullableBooleanInput : KotlinApplicatorInput

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF
    override fun getInputProvider() =
        inputProvider { expression: KtBinaryExpression ->
            val lhsType = expression.left?.getKtType() ?: return@inputProvider null
            // Returns a non-null input only if LHS has the nullable boolean type.
            if (lhsType.nullability.isNullable && lhsType.isBoolean) NullableBooleanInput() else null
        }

    override fun getApplicator() =
        applicator<KtBinaryExpression, NullableBooleanInput> {
            familyAndActionName(KotlinBundle.lazyMessage("inspection.nullable.boolean.elvis.display.name"))
            isApplicableByPsi { expression -> expression.isTargetOfNullableBooleanElvisInspection() }
            applyTo { expression, _ ->
                val lhs = expression.left ?: return@applyTo
                val rhs = expression.right as? KtConstantExpression ?: return@applyTo
                val parentWithNegation = expression.parentThroughParenthesisWithNegation()
                if (parentWithNegation == null) {
                    expression.replaceElvisWithBooleanEqualityOperation(lhs, rhs)
                } else {
                    parentWithNegation.replaceElvisWithBooleanEqualityOperation(lhs, rhs, hasNegation = true)
                }
            }
        }

    /**
     * A method checking whether the KtBinaryExpression is a target of the nullable boolean elvis inspection or not.
     *
     * To be a target of the nullable boolean elvis inspection,
     *  - The binary operator must be elvis.
     *  - RHS must be a boolean constant.
     *  - LHS must have a nullable boolean type. - This is checked by the "getInputProvider()" above.
     *
     * @return True if the KtBinaryExpression is a target of the nullable boolean elvis inspection.
     */
    private fun KtBinaryExpression.isTargetOfNullableBooleanElvisInspection(): Boolean {
        if (operationToken != KtTokens.ELVIS) return false
        val rhs = right ?: return false
        if (!KtPsiUtil.isBooleanConstant(rhs)) return false
        return true
    }

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
     * A method to replace the elvis operation with an equality check operation.
     *
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