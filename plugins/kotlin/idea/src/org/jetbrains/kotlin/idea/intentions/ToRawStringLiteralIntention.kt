// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.convertContentForRawString
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class ToRawStringLiteralIntention : SelfTargetingOffsetIndependentIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
    KotlinBundle.messagePointer("convert.to.raw.string.literal")
), LowPriorityAction {
    override fun isApplicableTo(element: KtStringTemplateExpression): Boolean {
        if (PsiTreeUtil.nextLeaf(element) is PsiErrorElement) {
            // Parse error right after the literal
            // the replacement may make things even worse, suppress the action
            return false
        }
        if (element.interpolationPrefix != null) return false // K2 only
        val text = element.text
        if (text.startsWith("\"\"\"")) return false // already raw

        val escapeEntries = element.entries.filterIsInstance<KtEscapeStringTemplateEntry>()
        for (entry in escapeEntries) {
            val c = entry.unescapedValue.singleOrNull() ?: return false
            if (Character.isISOControl(c) && c != '\n' && c != '\r') return false
        }

        val converted = convertContentForRawString(element)
        return !converted.contains("\"\"\"")
    }

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val startOffset = element.startOffset
        val endOffset = element.endOffset
        val currentOffset = editor?.caretModel?.currentCaret?.offset ?: startOffset

        val text = convertContentForRawString(element)
        val replaced = element.replaced(KtPsiFactory(element.project).createExpression("\"\"\"" + text + "\"\"\""))

        val offset = when {
            startOffset == currentOffset -> startOffset
            endOffset == currentOffset -> replaced.endOffset
            else -> minOf(currentOffset + 2, replaced.endOffset)
        }

        editor?.caretModel?.moveToOffset(offset)
    }

    private fun KtStringTemplateEntry.value() = if (this is KtEscapeStringTemplateEntry) this.unescapedValue else text
}