// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class ConvertToConcatenatedStringIntention : SelfTargetingOffsetIndependentIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
    KotlinBundle.lazyMessage("convert.template.to.concatenated.string")
), LowPriorityAction {
    override fun isApplicableTo(element: KtStringTemplateExpression): Boolean {
        if (element.lastChild.node.elementType != KtTokens.CLOSING_QUOTE) return false // not available for unclosed literal
        return element.entries.any { it is KtStringTemplateEntryWithExpression }
    }

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val tripleQuoted = isTripleQuoted(element.text!!)
        val quote = if (tripleQuoted) "\"\"\"" else "\""
        val entries = element.entries

        val targetEntries = entries
            .filterNot { it is KtStringTemplateEntryWithExpression && it.expression == null }
            .mapIndexed { index, entry ->
                entry to entry.toSeparateString(quote, convertExplicitly = (index == 0), isFinalEntry = (index == entries.lastIndex))
            }

        val text = buildString {
            targetEntries.forEachIndexed { index, (entry, entryText) ->
                var toBeAppended = entryText
                val prevEntryText = targetEntries.getOrNull(index - 1)?.second
                if (entryText.startsWith(quote) && prevEntryText?.endsWith(quote) == true && entry.isStringLiteral()) {
                    toBeAppended = toBeAppended.removePrefix(quote)
                } else if (prevEntryText != null) {
                    append("+")
                }
                val (nextEntry, nextEntryText) = targetEntries.getOrNull(index + 1) ?: (null to null)
                if (entryText.endsWith(quote) && nextEntryText?.startsWith(quote) == true && nextEntry?.isStringLiteral() == true) {
                    toBeAppended = toBeAppended.removeSuffix(quote)
                }
                append(toBeAppended)
            }
        }

        val replacement = KtPsiFactory(element).createExpression(text)
        element.replace(replacement)
    }

    private fun KtStringTemplateEntry.isStringLiteral() = expression == null || expression is KtStringTemplateExpression

    private fun isTripleQuoted(str: String) = str.startsWith("\"\"\"") && str.endsWith("\"\"\"")

    private fun KtStringTemplateEntry.toSeparateString(quote: String, convertExplicitly: Boolean, isFinalEntry: Boolean): String {
        if (this !is KtStringTemplateEntryWithExpression) return text.quote(quote)

        val expression = expression!! // checked before

        val text = if (needsParenthesis(expression, isFinalEntry))
            "(" + expression.text + ")"
        else
            expression.text

        return if (convertExplicitly && !expression.isStringExpression())
            "$text.toString()"
        else
            text
    }

    private fun needsParenthesis(expression: KtExpression, isFinalEntry: Boolean): Boolean = when (expression) {
        is KtBinaryExpression -> true
        is KtIfExpression -> expression.`else` !is KtBlockExpression && !isFinalEntry
        else -> false
    }

    private fun String.quote(quote: String) = quote + this + quote

    private fun KtExpression.isStringExpression() = KotlinBuiltIns.isString(analyze().getType(this))
}
