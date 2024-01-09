// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.coverage

import com.intellij.coverage.SwitchCoverageExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.psi.*


internal fun getSwitches(psiFile: PsiFile, range: TextRange): List<SwitchCoverageExpression> {
    val parent = getEnclosingParent(psiFile, range) ?: return emptyList()
    val expressions = mutableListOf<KtWhenExpression>()
    parent.accept(object : RangePsiVisitor(range) {
        override fun visitWhenExpression(expression: KtWhenExpression) {
            super.visitWhenExpression(expression)
            if (expression.textOffset in range) {
                expressions.add(expression)
            }
        }
    })
    return expressions.mapNotNull { switchExpression ->
        val expression = switchExpression.subjectExpression?.withoutParentheses()?.text ?: return@mapNotNull null
        val cases = extractCaseLabels(switchExpression).mapNotNull { if (it is KtExpression) it.withoutParentheses() else it }.map(PsiElement::getText)
        SwitchCoverageExpression(expression, cases, hasDefaultLabel(switchExpression))
    }
}

private open class RangePsiVisitor(private val range: TextRange) : KtTreeVisitorVoid() {
    override fun visitElement(element: PsiElement) {
        if (element.textOffset >= range.endOffset) return
        if (element.textOffset + element.textLength <= range.startOffset) return
        super.visitElement(element)
    }
}

private fun KtExpression.withoutParentheses(): KtExpression? {
    var expression = this
    while (expression is KtParenthesizedExpression) {
        expression = expression.expression ?: return null
    }
    return expression
}

private fun getEnclosingParent(psiFile: PsiFile, range: TextRange): PsiElement? {
    val elementAt = psiFile.findElementAt(range.startOffset) ?: return null
    return elementAt.parents(false).firstOrNull { it.textRange.contains(range) }
}

private fun extractCaseLabels(expression: KtWhenExpression): List<PsiElement> =
    expression.entries.filterNot(KtWhenEntry::isElse).flatMap { it.conditions.toList() }

private fun hasDefaultLabel(switchBlock: KtWhenExpression): Boolean =
    switchBlock.entries.any(KtWhenEntry::isElse)

