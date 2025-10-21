// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.psi.*

class FoldIfToReturnAsymmetricallyIntention : SelfTargetingRangeIntention<KtIfExpression>(
    KtIfExpression::class.java,
    KotlinBundle.messagePointer("replace.if.expression.with.return")
) {
    override fun applicabilityRange(element: KtIfExpression): TextRange? {
        if (BranchedFoldingUtils.getFoldableBranchedReturn(element.then) == null || element.`else` != null) {
            return null
        }

        val nextElement = KtPsiUtil.skipTrailingWhitespacesAndComments(element) as? KtReturnExpression
        if (nextElement?.returnedExpression == null) return null
        return element.ifKeyword.textRange
    }

    override fun applyTo(element: KtIfExpression, editor: Editor?) {
        val condition = element.condition ?: return
        val thenBranch = element.then ?: return
        val elseBranch = KtPsiUtil.skipTrailingWhitespacesAndComments(element) as? KtReturnExpression ?: return

        val psiFactory = KtPsiFactory(element.project)
        val newIfExpression = psiFactory.createIf(condition, thenBranch, elseBranch)

        val thenReturn = BranchedFoldingUtils.getFoldableBranchedReturn(newIfExpression.then!!) ?: return
        val elseReturn = BranchedFoldingUtils.getFoldableBranchedReturn(newIfExpression.`else`!!) ?: return

        thenReturn.replace(thenReturn.returnedExpression!!)
        elseReturn.replace(elseReturn.returnedExpression!!)

        element.replace(psiFactory.createExpressionByPattern("return $0", newIfExpression))
        elseBranch.delete()
    }
}