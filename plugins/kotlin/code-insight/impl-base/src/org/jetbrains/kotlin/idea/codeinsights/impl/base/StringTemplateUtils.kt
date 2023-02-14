// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.utils.isToString
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.ConstantValueKind

private const val TRIPLE_DOUBLE_QUOTE = "\"\"\""

/**
 * Recursively visits all operands of binary [expression] with plus and,
 * returns true if all operands do not have a new line. Otherwise, returns false.
 */
fun KtExpression.containNoNewLine(): Boolean {
    if (this is KtBinaryExpression && operationToken == KtTokens.PLUS) {
        val leftExpression = left ?: return false
        val rightExpression = right ?: return false
        return leftExpression.containNoNewLine() && rightExpression.containNoNewLine()
    }

    if (PsiUtilCore.hasErrorElementChild(this)) return false
    if (textContains('\n')) return false
    return true
}

/**
 * Returns true if [expression]
 * - is a binary expression with string plus, and
 * - has no operand with a new line.
 */
context(KtAnalysisSession)
fun isStringPlusExpressionWithoutNewLineInOperands(expression: KtBinaryExpression): Boolean {
    if (expression.operationToken != KtTokens.PLUS) return false

    if (!expression.containNoNewLine()) return false

    if (expression.getKtType()?.isString != true) return false
    val plusOperation = expression.operationReference.mainReference.resolveToSymbol() as? KtCallableSymbol
    val classContainingPlus = plusOperation?.getContainingSymbol() as? KtNamedClassOrObjectSymbol
    return classContainingPlus?.classIdIfNonLocal?.asSingleFqName() == StandardNames.FqNames.string.toSafe()
}

/**
 * Returns true if [element] is an expression with only string plus operations and its parent is not such an expression.
 *
 * Example:
 *  - For "a" + 'b' + "c", this function returns true for "a" + 'b' + "c", but returns false for 'b' + "c" since 'b' + "c" has a parent
 *    (i.e., "a" + 'b' + "c") that consists of only string plus operations.
 */
context(KtAnalysisSession)
fun isFirstStringPlusExpressionWithoutNewLineInOperands(element: KtBinaryExpression): Boolean {
    if (!isStringPlusExpressionWithoutNewLineInOperands(element)) return false

    val parent = element.parent
    if (parent is KtBinaryExpression && isStringPlusExpressionWithoutNewLineInOperands(parent)) return false

    return true
}

/**
 * After removing parenthesis and toString(), the expression can be replaced with the given [KtExpression] in a string template.
 * For example, `foo.toString()` can be "$foo" in the string template.
 */
private fun KtExpression.dropToStringAndParenthesis(): KtExpression =
    KtPsiUtil.safeDeparenthesize(this).let { expression ->
        val dotQualifiedExpression = expression as? KtDotQualifiedExpression
        when {
            dotQualifiedExpression != null && dotQualifiedExpression.isToString() && dotQualifiedExpression.receiverExpression !is KtSuperExpression ->
                dotQualifiedExpression.receiverExpression

            expression is KtLambdaExpression && expression.parent is KtLabeledExpression ->
                this

            else ->
                expression
        }
    }

/**
 * A function to build a string or string template for an expression [expr].
 *
 * @param forceBraces If true, this function will add braces to the built string e.g., "${foo}" instead of "$foo"
 *
 * Example:
 *  - When [expr] is a reference to a variable foo, this function build to "$foo"
 *  - For 2.3f, build "2.3f"
 *
 * Usage Example:
 *   When we convert
 *     "a" + 1 + 'b' + foo + 2.3f + bar -> "a1b${foo}2.3f{bar}"
 *   this function can convert `bar` to "${bar}", `2.3f` to "2.3f", ...
 */
context(KtAnalysisSession)
private fun buildStringTemplateForExpression(expr: KtExpression?, forceBraces: Boolean): String {
    if (expr == null) return ""
    val expression = expr.dropToStringAndParenthesis()

    val expressionText = expression.text
    val defaultStringTemplateForExpression = "\${$expressionText}"
    when (expression) {
        is KtConstantExpression -> return expression.buildStringTemplateForExpression(forceBraces) ?: defaultStringTemplateForExpression

        is KtStringTemplateExpression -> return expression.buildStringTemplateForExpression(forceBraces)

        is KtNameReferenceExpression ->
            return "$" + (if (forceBraces) "{$expressionText}" else expressionText)

        is KtThisExpression ->
            return "$" + (if (forceBraces || expression.labelQualifier != null) "{$expressionText}" else expressionText)
    }

    return defaultStringTemplateForExpression
}

context(KtAnalysisSession)
private fun KtConstantExpression.buildStringTemplateForExpression(forceBraces: Boolean): String? {
    val constantValue = evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION) ?: return "\${${text}}"
    val isChar = constantValue.constantValueKind == ConstantValueKind.Char
    val stringValue = if (isChar) "${constantValue.value}" else constantValue.renderAsKotlinConstant()
    if (isChar || stringValue == text) {
        return StringUtil.escapeStringCharacters(
            stringValue.length, stringValue, if (forceBraces) "\"$" else "\"", StringBuilder()
        ).toString()
    }
    return null
}

private fun KtStringTemplateExpression.buildStringTemplateForExpression(forceBraces: Boolean): String {
    val base = if (text.startsWith(TRIPLE_DOUBLE_QUOTE) && text.endsWith(TRIPLE_DOUBLE_QUOTE)) {
        val unquoted =
            text.substring(TRIPLE_DOUBLE_QUOTE.length, text.length - TRIPLE_DOUBLE_QUOTE.length)
        StringUtil.escapeStringCharacters(unquoted)
    } else {
        StringUtil.unquoteString(text)
    }

    if (forceBraces) {
        if (base.endsWith(char = '$')) {
            return base.dropLast(n = 1) + "\\$"
        } else {
            val lastPart = children.lastOrNull()
            if (lastPart is KtSimpleNameStringTemplateEntry) {
                return base.dropLast(lastPart.textLength) + "\${" + lastPart.text.drop(n = 1) + "}"
            }
        }
    }
    return base
}

context(KtAnalysisSession)
private fun foldOperandsOfBinaryExpression(left: KtExpression?, right: String, factory: KtPsiFactory): KtStringTemplateExpression {
    val forceBraces = right.isNotEmpty() && right.first() != '$' && right.first().isJavaIdentifierPart()

    return if (left is KtBinaryExpression && isStringPlusExpressionWithoutNewLineInOperands(left)) {
        val leftRight = buildStringTemplateForExpression(left.right, forceBraces)
        foldOperandsOfBinaryExpression(left.left, leftRight + right, factory)
    } else {
        val leftText = buildStringTemplateForExpression(left, forceBraces)
        factory.createExpression("\"$leftText$right\"") as KtStringTemplateExpression
    }
}

context(KtAnalysisSession)
fun buildStringTemplateForBinaryExpression(expression: KtBinaryExpression): KtStringTemplateExpression {
    val rightText = buildStringTemplateForExpression(expression.right, forceBraces = false)
    return foldOperandsOfBinaryExpression(expression.left, rightText, KtPsiFactory(expression))
}

context(KtAnalysisSession)
fun canConvertToStringTemplate(expression: KtBinaryExpression): Boolean {
    if (expression.textContains('\n')) return false

    val entries = buildStringTemplateForBinaryExpression(expression).entries
    return entries.none { it is KtBlockStringTemplateEntry }
            && entries.any { it !is KtLiteralStringTemplateEntry && it !is KtEscapeStringTemplateEntry }
            && entries.any { it is KtLiteralStringTemplateEntry }
}

private fun convertContent(element: KtStringTemplateExpression): String {
    val text = buildString {
        val entries = element.entries
        for ((index, entry) in entries.withIndex()) {
            val value = entry.value()

            if (value.endsWith("$") && index < entries.size - 1) {
                val nextChar = entries[index + 1].value().first()
                if (nextChar.isJavaIdentifierStart() || nextChar == '{') {
                    append("\${\"$\"}")
                    continue
                }
            }

            append(value)
        }
    }

    return StringUtilRt.convertLineSeparators(text, "\n")
}

private fun hasTrailingSpaces(text: String): Boolean {
    var afterSpace = false
    for (c in text) {
        if ((c == '\n' || c == '\r') && afterSpace) return true
        afterSpace = c == ' ' || c == '\t'
    }

    return false
}

private fun KtStringTemplateEntry.value() = if (this is KtEscapeStringTemplateEntry) this.unescapedValue else text

fun KtStringTemplateExpression.canBeConvertedToStringLiteral(): Boolean {
    if (PsiTreeUtil.nextLeaf(this) is PsiErrorElement) {
        // Parse error right after the literal
        // the replacement may make things even worse, suppress the action
        return false
    }
    val text = text
    if (text.startsWith("\"\"\"")) return false // already raw

    val escapeEntries = entries.filterIsInstance<KtEscapeStringTemplateEntry>()
    for (entry in escapeEntries) {
        val c = entry.unescapedValue.singleOrNull() ?: return false
        if (Character.isISOControl(c) && c != '\n' && c != '\r') return false
    }

    val converted = convertContent(this)
    return !converted.contains("\"\"\"") && !hasTrailingSpaces(converted)
}

fun KtStringTemplateExpression.convertToStringLiteral(): KtExpression {
    val text = convertContent(this)
    return replaced(KtPsiFactory(project).createExpression("\"\"\"" + text + "\"\"\""))
}