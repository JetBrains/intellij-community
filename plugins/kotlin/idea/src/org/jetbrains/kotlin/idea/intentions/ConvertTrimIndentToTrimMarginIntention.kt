// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class ConvertTrimIndentToTrimMarginIntention : SelfTargetingIntention<KtCallExpression>(
    KtCallExpression::class.java, KotlinBundle.lazyMessage("convert.to.trim.margin")
) {
    override fun isApplicableTo(element: KtCallExpression, caretOffset: Int): Boolean {
        val template = (element.getQualifiedExpressionForSelector()?.receiverExpression as? KtStringTemplateExpression) ?: return false
        if (!template.text.startsWith("\"\"\"")) return false

        val callee = element.calleeExpression ?: return false
        if (callee.text != "trimIndent" || callee.getCallableDescriptor()?.fqNameSafe != FqName("kotlin.text.trimIndent")) return false

        return template.isSurroundedByLineBreaksOrBlanks()
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val qualifiedExpression = element.getQualifiedExpressionForSelector()
        val template = (qualifiedExpression?.receiverExpression as? KtStringTemplateExpression) ?: return
        val indent = template.calculateIndent()
        val newTemplate = buildString {
            template.entries.forEach { entry ->
                val text = entry.text
                if (text.isLineBreakOrBlank()) {
                    append(text)
                } else {
                    append(indent)
                    append("|")
                    append(text.drop(indent.length))
                }
            }
        }
        qualifiedExpression.replace(KtPsiFactory(element).createExpression("\"\"\"$newTemplate\"\"\".trimMargin()"))
    }

    companion object {
        fun KtStringTemplateExpression.isSurroundedByLineBreaksOrBlanks(): Boolean {
            val entries = entries
            return listOfNotNull(entries.firstOrNull(), entries.lastOrNull()).all { it.text.isLineBreakOrBlank() }
        }

        fun String.isLineBreakOrBlank(): Boolean =
            this == "\n" || this.isBlank()

        fun KtStringTemplateExpression.calculateIndent(): String =
            entries.asSequence().mapNotNull { stringTemplateEntry ->
                val text = stringTemplateEntry.text
                if (text.isLineBreakOrBlank()) null else text.takeWhile { it.isWhitespace() }
            }.minByOrNull { it.length } ?: ""
    }
}
