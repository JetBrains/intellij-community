// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.isToString
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.ConstantValueKind

private const val TRIPLE_DOUBLE_QUOTE = "\"\"\""

class ConvertToStringTemplateInspection : IntentionBasedInspection<KtBinaryExpression>(
    ConvertToStringTemplateIntention::class,
    ::canConvertToStringTemplate,
    problemText = KotlinBundle.message("convert.concatenation.to.template.before.text")
)

/**
 * A class for convert-to-string-template intention.
 *
 * Example: "a" + 1 + 'b' + foo + 2.3f + bar -> "a1b${foo}2.3f{bar}"
 */
open class ConvertToStringTemplateIntention : SelfTargetingOffsetIndependentIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.lazyMessage("convert.concatenation.to.template")
) {
    /**
     * [element] is applicable for this intention if
     * - it is an expression with only string plus operations, and
     * - its parent is not an expression with only string plus operations
     *   - which helps us to avoid handling the child multiple times
     *     e.g., for "a" + 'b' + "c", we do not want to visit both 'b' + "c" and "a" + 'b' + "c" since 'b' + "c" will be handled
     *     in "a" + 'b' + "c".
     */
    override fun isApplicableTo(element: KtBinaryExpression): Boolean {
        if (!isStringPlusExpressionWithoutNewLineInOperands(element)) return false

        val parent = element.parent
        if (parent is KtBinaryExpression && isStringPlusExpressionWithoutNewLineInOperands(parent)) return false

        return true
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val replacement = buildStringTemplateForBinaryExpression(element)
        runWriteActionIfPhysical(element) {
            element.replaced(replacement)
        }
    }
}

/**
 * Recursively visits all operands of binary [expression] with plus and,
 * returns true if all operands do not have a new line. Otherwise, returns false.
 */
private fun hasNoPlusOperandWithNewLine(expression: KtExpression): Boolean {
    if (expression is KtBinaryExpression && expression.operationToken == KtTokens.PLUS) {
        val left = expression.left ?: return false
        val right = expression.right ?: return false
        return hasNoPlusOperandWithNewLine(left) && hasNoPlusOperandWithNewLine(right)
    }

    if (PsiUtilCore.hasErrorElementChild(expression)) return false
    if (expression.textContains('\n')) return false
    return true
}

/**
 * Returns true if [expression]
 * - is a binary expression with string plus, and
 * - has no operand with a new line.
 */
@OptIn(KtAllowAnalysisOnEdt::class)
private fun isStringPlusExpressionWithoutNewLineInOperands(expression: KtBinaryExpression): Boolean {
    if (expression.operationToken != KtTokens.PLUS) return false

    if (!hasNoPlusOperandWithNewLine(expression)) return false

    return allowAnalysisOnEdt { analyze(expression) { expression.getKtType()?.isString == true } }
}

/**
 * A function to build a string or string template for a single operand of a binary expression.
 *
 * @param expr The single operand of a binary expression as an expression.
 *
 * Example:
 *   When we convert
 *     "a" + 1 + 'b' + foo + 2.3f + bar -> "a1b${foo}2.3f{bar}"
 *   this function converts `bar` to "${bar}", `2.3f` to "2.3f", ...
 */
@OptIn(KtAllowAnalysisOnEdt::class)
private fun buildStringTemplateForBinaryOperand(expr: KtExpression?, forceBraces: Boolean): String {
    if (expr == null) return ""
    val expression = KtPsiUtil.safeDeparenthesize(expr).let { expression ->
        val dotQualifiedExpression = expression as? KtDotQualifiedExpression
        when {
            dotQualifiedExpression != null && allowAnalysisOnEdt { dotQualifiedExpression.isToString() } && dotQualifiedExpression.receiverExpression !is KtSuperExpression ->
                dotQualifiedExpression.receiverExpression
            expression is KtLambdaExpression && expression.parent is KtLabeledExpression ->
                expr
            else ->
                expression
        }
    }

    val expressionText = expression.text
    when (expression) {
        is KtConstantExpression -> {
            allowAnalysisOnEdt {
                analyze(expression) {
                    val constantValue = expression.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION)
                        ?: return "\${${expressionText}}"
                    val isChar = constantValue.constantValueKind == ConstantValueKind.Char
                    val stringValue = if (isChar) "${constantValue.value}" else constantValue.renderAsKotlinConstant()
                    if (isChar || stringValue == expressionText) {
                        return StringUtil.escapeStringCharacters(
                            stringValue.length, stringValue, if (forceBraces) "\"$" else "\"", StringBuilder()
                        ).toString()
                    }
                }
            }
        }

        is KtStringTemplateExpression -> {
            val base = if (expressionText.startsWith(TRIPLE_DOUBLE_QUOTE) && expressionText.endsWith(TRIPLE_DOUBLE_QUOTE)) {
                val unquoted =
                    expressionText.substring(TRIPLE_DOUBLE_QUOTE.length, expressionText.length - TRIPLE_DOUBLE_QUOTE.length)
                StringUtil.escapeStringCharacters(unquoted)
            } else {
                StringUtil.unquoteString(expressionText)
            }

            if (forceBraces) {
                if (base.endsWith(char = '$')) {
                    return base.dropLast(n = 1) + "\\$"
                } else {
                    val lastPart = expression.children.lastOrNull()
                    if (lastPart is KtSimpleNameStringTemplateEntry) {
                        return base.dropLast(lastPart.textLength) + "\${" + lastPart.text.drop(n = 1) + "}"
                    }
                }
            }
            return base
        }

        is KtNameReferenceExpression ->
            return "$" + (if (forceBraces) "{$expressionText}" else expressionText)

        is KtThisExpression ->
            return "$" + (if (forceBraces || expression.labelQualifier != null) "{$expressionText}" else expressionText)
    }

    return "\${$expressionText}"
}

private fun foldOperandsOfBinaryExpression(left: KtExpression?, right: String, factory: KtPsiFactory): KtStringTemplateExpression {
    val forceBraces = right.isNotEmpty() && right.first() != '$' && right.first().isJavaIdentifierPart()

    return if (left is KtBinaryExpression && isStringPlusExpressionWithoutNewLineInOperands(left)) {
        val leftRight = buildStringTemplateForBinaryOperand(left.right, forceBraces)
        foldOperandsOfBinaryExpression(left.left, leftRight + right, factory)
    } else {
        val leftText = buildStringTemplateForBinaryOperand(left, forceBraces)
        factory.createExpression("\"$leftText$right\"") as KtStringTemplateExpression
    }
}

private fun buildStringTemplateForBinaryExpression(expression: KtBinaryExpression): KtStringTemplateExpression {
    val rightText = buildStringTemplateForBinaryOperand(expression.right, forceBraces = false)
    return foldOperandsOfBinaryExpression(expression.left, rightText, KtPsiFactory(expression))
}

private fun canConvertToStringTemplate(expression: KtBinaryExpression): Boolean {
    if (expression.textContains('\n')) return false

    val entries = buildStringTemplateForBinaryExpression(expression).entries
    return entries.none { it is KtBlockStringTemplateEntry }
            && entries.none { it !is KtLiteralStringTemplateEntry && it !is KtEscapeStringTemplateEntry }
            && entries.any { it is KtLiteralStringTemplateEntry }
}
