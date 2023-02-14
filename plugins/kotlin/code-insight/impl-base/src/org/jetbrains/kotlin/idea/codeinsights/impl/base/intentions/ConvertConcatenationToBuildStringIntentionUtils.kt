// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.tailComments
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.codeinsights.impl.base.psi.generateBuildStringCallWithExtras
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

fun convertConcatenationToBuildStringCall(element: KtBinaryExpression): KtExpression {
    val operands = element.collectOperands()
    val operandsWithExtras = operands.mapIndexed {
        index, expression -> transformOperand(expression, isLast = index == operands.lastIndex)
    }
    val call = generateBuildStringCallWithExtras(element.project, operandsWithExtras)
    element.deleteTailComments()
    val replacedElement = element.replaced(call)
    replacedElement.reformat(canChangeWhiteSpacesOnly = true)
    return replacedElement
}

private fun transformOperand(expression: KtExpression, isLast: Boolean): Pair<KtExpression, List<PsiElement>> {
    val unpackedExpression = if (expression is KtStringTemplateExpression) {
        // Unpack string templates that contain a single expression.
        (expression.entries.singleOrNull() as? KtStringTemplateEntryWithExpression)?.expression ?: expression
    } else expression

    // Tail comments on next lines should only be collected if the current operand is not the last. Otherwise, unrelated comments that
    // follow the concatenation may be falsely collected. Such comments are not deleted by `deleteTailComments` either and would be
    // duplicated inside the `buildString` call.
    val tailComments = expression.tailComments(allowNewline = !isLast) { it.elementType == KtTokens.PLUS }

    return Pair(unpackedExpression, tailComments)
}

private fun KtBinaryExpression.collectOperands(): List<KtExpression> {
    val expressions = mutableListOf<KtExpression>()
    fun collect(expression: KtExpression?) {
        when (expression) {
            is KtBinaryExpression -> {
                collect(expression.left)
                collect(expression.right)
            }
            is KtExpression ->
                expressions.add(expression)
        }
    }
    collect(this)
    return expressions
}

private fun KtExpression.deleteTailComments() {
    siblings(withSelf = false)
        .takeWhile { !it.isWhiteSpaceWithLineBreak() }
        .filter { it is PsiComment }
        .forEach { it.delete() }
}

private fun PsiElement.isWhiteSpaceWithLineBreak() = this is PsiWhiteSpace && this.textContains('\n')