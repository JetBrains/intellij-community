// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MergeIfsUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.MergeIfsUtils.asSingleIfExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class MergeIfsIntention : SelfTargetingIntention<KtExpression>(KtExpression::class.java, KotlinBundle.messagePointer("merge.if.s")) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean =
        element.ifExpression()?.isApplicable(caretOffset) == true

    override fun applyTo(element: KtExpression, editor: Editor?) {
        element.ifExpression()?.let(MergeIfsUtils::mergeNestedIf)
    }

    private fun KtExpression.ifExpression(): KtIfExpression? = when (this) {
        is KtIfExpression -> this
        is KtBlockExpression -> parent.parent as? KtIfExpression
        else -> null
    }

    private fun KtIfExpression.isApplicable(caretOffset: Int): Boolean {
        if (`else` != null) return false
        val then = then ?: return false

        val nestedIf = then.asSingleIfExpression() ?: return false
        if (nestedIf.`else` != null) return false

        return caretOffset !in TextRange(nestedIf.startOffset, nestedIf.endOffset + 1)
    }
}