// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted

abstract class ConvertToConcatenatedStringIntentionBase : SelfTargetingOffsetIndependentIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
    KotlinBundle.lazyMessage("convert.template.to.concatenated.string")
), LowPriorityAction {
    override fun isApplicableTo(element: KtStringTemplateExpression): Boolean {
        if (element.lastChild.node.elementType != KtTokens.CLOSING_QUOTE) return false // not available for unclosed literal
        if (element.interpolationPrefix?.textLength?.let { it > 1 } == true) return false // not supported for multi-dollar strings
        return element.entries.any { it is KtStringTemplateEntryWithExpression }
    }

    override fun startInWriteAction(): Boolean = false

    override fun getElementToMakeWritable(currentFile: PsiFile) = currentFile

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        checkNotNull(element.text) { "Failed to get template expression's text" }
        val tripleQuoted = !element.isSingleQuoted()
        val quote = if (tripleQuoted) "\"\"\"" else "\""
        val entries = element.entries.filterNot { it is KtStringTemplateEntryWithExpression && it.expression == null }

        val convertFirstEntryExplicitly = (entries.firstOrNull() as? KtStringTemplateEntryWithExpression)?.expression?.let {
            !checkIfExpressionIsStringFromModalView(it)
        } ?: false

        val entryTexts = entries.mapIndexed { index, entry ->
            val entryText = entry.toSeparateString(quote, convertExplicitly = (index == 0) && convertFirstEntryExplicitly)
            val entryIsString = entryText.startsWith(quote) && entryText.endsWith(quote) && entry.isStringLiteral()
            entryText to entryIsString
        }

        // merge all consecutive string literals
        val targetTexts = entryTexts.foldIndexed(mutableListOf<String>()) { index, texts, (currText, currIsString) ->
            val prevIsString = entryTexts.getOrNull(index - 1)?.second ?: false
            val nextIsString = entryTexts.getOrNull(index + 1)?.second ?: false
            var textToBeMerged = currText
            if (currIsString && nextIsString) textToBeMerged = textToBeMerged.removeSuffix(quote)
            if (currIsString && prevIsString) {
                textToBeMerged = textToBeMerged.removePrefix(quote)
                texts[texts.lastIndex] += textToBeMerged
            } else {
                texts.add(textToBeMerged)
            }
            texts
        }

        val text = targetTexts.joinToString("+")
        val replacement = KtPsiFactory(element).createExpression(text).safeDeparenthesizeOperands()
        runWriteActionIfPhysical(element) {
            element.replace(replacement)
        }
    }

    private fun KtExpression.safeDeparenthesizeOperands(): KtExpression {
        if (this is KtBinaryExpression) {
            val deparenthesizedLeft = this.left!!.safeDeparenthesizeOperands()
            val deparenthesizedRight = this.right!!.safeDeparenthesizeOperands()
            return KtPsiFactory(this.project).createExpressionByPattern(
                "$0+$1",
                deparenthesizedLeft.text,
                deparenthesizedRight.text
            )
        }
        if (this is KtParenthesizedExpression && KtPsiUtil.areParenthesesUseless(this)) {
            return KtPsiUtil.safeDeparenthesize(this, true)
        }
        return this
    }

    private fun KtStringTemplateEntry.isStringLiteral(): Boolean = expression == null || expression is KtStringTemplateExpression

    private fun isTripleQuoted(str: String): Boolean = str.startsWith("\"\"\"") && str.endsWith("\"\"\"")

    private fun KtStringTemplateEntry.toSeparateString(quote: String, convertExplicitly: Boolean): String {
        if (this !is KtStringTemplateEntryWithExpression) return text.quote(quote)

        val expression = expression!! // checked before

        val text = if (needsParenthesis(expression))
            "(${expression.text})"
        else
            expression.text

        return if (convertExplicitly)
            "$text.toString()"
        else
            text
    }

    private fun needsParenthesis(expression: KtExpression): Boolean = when (expression) {
        is KtPostfixExpression -> false
        is KtAnnotatedExpression,
        is KtLabeledExpression,
        is KtOperationExpression -> true

        is KtIfExpression -> expression.`else` !is KtBlockExpression
        else -> false
    }

    private fun String.quote(quote: String): String = quote + this + quote

    private fun checkIfExpressionIsStringFromModalView(expression: KtExpression): Boolean {
        return ActionUtil.underModalProgress(
            expression.project,
            KotlinBundle.message("convert.to.concatenated.string.statement.analyzing.entry.type")
        ) {
            isExpressionOfStringType(expression)
        }
    }

    /**
     * Tells whether given [expression] is of the string type.
     *
     * Called from cancellable modal progress and under read action, so it's safe to use resolve here.
     */
    abstract fun isExpressionOfStringType(expression: KtExpression): Boolean
}
