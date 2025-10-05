// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.BranchedUnfoldingUtils
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class UnfoldAssignmentToIfIntention : SelfTargetingRangeIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.messagePointer("replace.assignment.with.if.expression")
), LowPriorityAction {
    override fun applicabilityRange(element: KtBinaryExpression): TextRange? {
        if (element.operationToken !in KtTokens.ALL_ASSIGNMENTS) return null
        if (element.left == null) return null
        val right = element.right as? KtIfExpression ?: return null
        return TextRange(element.startOffset, right.ifKeyword.endOffset)
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?): Unit =
        BranchedUnfoldingUtils.unfoldAssignmentToIf(element) { editor?.moveCaret(it) }
}