// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.entryPrefixLength
import org.jetbrains.kotlin.idea.core.RestoreCaret
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class InsertCurlyBracesToTemplateIntention : SelfTargetingOffsetIndependentIntention<KtSimpleNameStringTemplateEntry>(
  KtSimpleNameStringTemplateEntry::class.java, KotlinBundle.messagePointer("insert.curly.braces.around.variable")
), LowPriorityAction {

    override fun isApplicableTo(element: KtSimpleNameStringTemplateEntry): Boolean = true

    override fun applyTo(element: KtSimpleNameStringTemplateEntry, editor: Editor?) {
        val expression = element.expression ?: return

        with(RestoreCaret(expression, editor)) {
            val entryPrefixLength = element.getStrictParentOfType<KtStringTemplateExpression>()?.entryPrefixLength ?: return
            val newEntry = KtPsiFactory(element.project).createMultiDollarBlockStringTemplateEntry(expression, entryPrefixLength)
            val wrapped = element.replace(newEntry)
            val afterExpression = (wrapped as? KtStringTemplateEntryWithExpression)?.expression ?: return

            restoreCaret(afterExpression, defaultOffset = { it.endOffset })
        }
    }
}