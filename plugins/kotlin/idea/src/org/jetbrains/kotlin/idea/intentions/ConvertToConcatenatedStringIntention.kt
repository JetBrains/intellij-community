// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
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

        val entryStringList = entries
            .filterNot { it is KtStringTemplateEntryWithExpression && it.expression == null }
            .mapIndexed { index, entry ->
                entry.toSeparateString(quote, convertExplicitly = (index == 0), isFinalEntry = (index == entries.lastIndex))
            }
        val text = buildString {
            entryStringList.forEachIndexed { index, entry ->
                var toBeAppended = entry
                val prevEntry = entryStringList.getOrNull(index - 1)
                if (entry.startsWith(quote) && prevEntry?.endsWith(quote) == true) {
                    toBeAppended = toBeAppended.removePrefix(quote)
                } else if (prevEntry != null) {
                    append("+")
                }
                val nextEntry = entryStringList.getOrNull(index + 1)
                if (entry.endsWith(quote) && nextEntry?.startsWith(quote) == true) {
                    toBeAppended = toBeAppended.removeSuffix(quote)
                }
                append(toBeAppended)
            }
        }

        val replacement = KtPsiFactory(element).createExpression(text)
        element.replace(replacement)
    }

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
