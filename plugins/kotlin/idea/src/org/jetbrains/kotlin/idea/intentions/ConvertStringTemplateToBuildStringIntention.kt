// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.psi.*

class ConvertStringTemplateToBuildStringIntention : SelfTargetingIntention<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
    KotlinBundle.lazyMessage("convert.string.template.to.build.string"),
), LowPriorityAction {
    override fun isApplicableTo(element: KtStringTemplateExpression, caretOffset: Int): Boolean {
        return !element.text.startsWith("\"\"\"") && !element.mustBeConstant()
    }

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val entries: MutableList<MutableList<KtStringTemplateEntry>> = mutableListOf()
        var lastEntry: KtStringTemplateEntry? = null
        element.entries.forEachIndexed { index, entry ->
            if (index == 0 || entry is KtStringTemplateEntryWithExpression || lastEntry is KtStringTemplateEntryWithExpression) {
                entries.add(mutableListOf(entry))
            } else {
                entries.last().add(entry)
            }
            lastEntry = entry
        }
        val buildStringCall = KtPsiFactory(element).buildExpression {
            appendFixedText("kotlin.text.buildString {\n")
            entries.forEach {
                val singleEntry = it.singleOrNull()
                appendFixedText("append(")
                if (singleEntry is KtStringTemplateEntryWithExpression) {
                    appendExpression(singleEntry.expression)
                } else {
                    appendFixedText("\"")
                    it.forEach { entry -> appendNonFormattedText(entry.text) }
                    appendFixedText("\"")
                }
                appendFixedText(")\n")
            }
            appendFixedText("}")
        }
        val replaced = element.replaced(buildStringCall)
        ShortenReferences.DEFAULT.process(replaced)
    }
}