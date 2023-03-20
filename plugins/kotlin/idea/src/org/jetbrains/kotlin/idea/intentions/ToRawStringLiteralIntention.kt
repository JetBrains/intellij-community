// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class ToRawStringLiteralIntention : SelfTargetingOffsetIndependentIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
    KotlinBundle.lazyMessage("to.raw.string.literal")
), LowPriorityAction {
    override fun isApplicableTo(element: KtStringTemplateExpression): Boolean {
        if (PsiTreeUtil.nextLeaf(element) is PsiErrorElement) {
            // Parse error right after the literal
            // the replacement may make things even worse, suppress the action
            return false
        }
        val text = element.text
        if (text.startsWith("\"\"\"")) return false // already raw

        val escapeEntries = element.entries.filterIsInstance<KtEscapeStringTemplateEntry>()
        for (entry in escapeEntries) {
            val c = entry.unescapedValue.singleOrNull() ?: return false
            if (Character.isISOControl(c) && c != '\n' && c != '\r') return false
        }

        val converted = convertContent(element)
        return !converted.contains("\"\"\"") && !hasTrailingSpaces(converted)
    }

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val startOffset = element.startOffset
        val endOffset = element.endOffset
        val currentOffset = editor?.caretModel?.currentCaret?.offset ?: startOffset

        val text = convertContent(element)
        val replaced = element.replaced(KtPsiFactory(element.project).createExpression("\"\"\"" + text + "\"\"\""))

        val offset = when {
            startOffset == currentOffset -> startOffset
            endOffset == currentOffset -> replaced.endOffset
            else -> minOf(currentOffset + 2, replaced.endOffset)
        }

        editor?.caretModel?.moveToOffset(offset)
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
}