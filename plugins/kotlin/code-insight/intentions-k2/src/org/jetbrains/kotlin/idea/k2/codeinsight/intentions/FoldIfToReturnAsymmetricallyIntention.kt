// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.k2.refactoring.util.BranchedFoldingUtils
import org.jetbrains.kotlin.psi.*

class FoldIfToReturnAsymmetricallyIntention : KotlinApplicableModCommandAction.Simple<KtIfExpression>(KtIfExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.if.expression.with.return")

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> {
        if (BranchedFoldingUtils.getFoldableBranchedReturn(element.then) == null || element.`else` != null) {
            return emptyList()
        }

        val nextElement = KtPsiUtil.skipTrailingWhitespacesAndComments(element) as? KtReturnExpression
        if (nextElement?.returnedExpression == null) return emptyList()
        return ApplicabilityRange.self(element.ifKeyword)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtIfExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
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