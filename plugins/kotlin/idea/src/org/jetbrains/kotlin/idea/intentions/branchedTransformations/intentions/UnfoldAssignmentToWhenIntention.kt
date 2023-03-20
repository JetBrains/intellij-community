// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedUnfoldingUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class UnfoldAssignmentToWhenIntention :
  SelfTargetingRangeIntention<KtBinaryExpression>(
        KtBinaryExpression::class.java,
        KotlinBundle.lazyMessage("replace.assignment.with.when.expression")
    ),
  LowPriorityAction {
    override fun applicabilityRange(element: KtBinaryExpression): TextRange? {
        if (element.operationToken !in KtTokens.ALL_ASSIGNMENTS) return null
        if (element.left == null) return null
        val right = element.right as? KtWhenExpression ?: return null
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(right)) return null
        if (right.entries.any { it.expression == null }) return null
        return TextRange(element.startOffset, right.whenKeyword.endOffset)
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) = BranchedUnfoldingUtils.unfoldAssignmentToWhen(element, editor)
}