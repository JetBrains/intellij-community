// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.elementType
import com.intellij.util.application
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightReceiverUsagesHandler
import org.jetbrains.kotlin.idea.highlighter.ReceiverInfoSearcher.findReceiverInfoForUsageHighlighting
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtReferenceExpression

class HighlightReceiverUsagesIntention : SelfTargetingOffsetIndependentIntention<KtElement>(
    KtElement::class.java,
    KotlinBundle.messagePointer("highlight.usages.of.receiver")
), LowPriorityAction {
    override fun isApplicableTo(element: KtElement): Boolean =
        findReceiverInfo(element) != null

    private fun findReceiverInfo(element: KtElement) =
        if (element is KtReferenceExpression && element.firstChild?.elementType == KtTokens.THIS_KEYWORD) {
            findReceiverInfoForUsageHighlighting(element.firstChild)
        } else {
            findReceiverInfoForUsageHighlighting(element)
        }

    override fun startInWriteAction(): Boolean = false

    override fun applyTo(element: KtElement, editor: Editor?) {
        if (editor == null) return

        application.executeOnPooledThread {
            ReadAction.computeCancellable<Unit, Throwable> {
                val info = findReceiverInfo(element) ?: return@computeCancellable
                KotlinHighlightReceiverUsagesHandler(info, editor, false).highlightUsages()
            }
        }
    }
}
