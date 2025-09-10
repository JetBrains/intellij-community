// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.intentions.ConvertTrimIndentToTrimMarginIntention.Holder.calculateIndent
import org.jetbrains.kotlin.idea.intentions.ConvertTrimIndentToTrimMarginIntention.Holder.isStartOfLine
import org.jetbrains.kotlin.idea.intentions.ConvertTrimIndentToTrimMarginIntention.Holder.isSurroundedByLineBreaksOrBlanks
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class ConvertTrimMarginToTrimIndentIntention : SelfTargetingIntention<KtCallExpression>(
    KtCallExpression::class.java, KotlinBundle.messagePointer("convert.to.trim.indent")
) {
    override fun isApplicableTo(element: KtCallExpression, caretOffset: Int): Boolean {
        val template = (element.getQualifiedExpressionForSelector()?.receiverExpression as? KtStringTemplateExpression) ?: return false
        if (!template.text.startsWith("\"\"\"")) return false

        val callee = element.calleeExpression ?: return false
        if (callee.text != "trimMargin" || callee.getCallableDescriptor()?.fqNameSafe != FqName("kotlin.text.trimMargin")) return false

        if (!template.isSurroundedByLineBreaksOrBlanks()) return false

        val marginPrefix = element.marginPrefix() ?: return false
        return template.entries.all { entry ->
            !entry.isStartOfLine() || entry.text.dropWhile { it.isWhitespace() }.startsWith(marginPrefix)
        }
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val qualifiedExpression = element.getQualifiedExpressionForSelector()
        val template = (qualifiedExpression?.receiverExpression as? KtStringTemplateExpression) ?: return
        val marginPrefix = element.marginPrefix() ?: return
        val indent = template.calculateIndent()
        val newTemplate = buildString {
            template.entries.forEach { entry ->
                val text = entry.text
                if (entry.isStartOfLine()) {
                    append(indent)
                    append(entry.text.dropWhile { it.isWhitespace() }.replaceFirst(marginPrefix, ""))
                } else {
                    append(text)
                }
            }
        }
        qualifiedExpression.replace(KtPsiFactory(element.project).createExpression("\"\"\"$newTemplate\"\"\".trimIndent()"))
    }
}

private fun KtCallExpression.marginPrefix(): String? {
    val argument = valueArguments.firstOrNull()?.getArgumentExpression()
    if (argument != null) {
        if (argument !is KtStringTemplateExpression) return null
        val entry = argument.entries.toList().singleOrNull() as? KtLiteralStringTemplateEntry ?: return null
        return entry.text.replace("\"", "")
    }
    return "|"
}
