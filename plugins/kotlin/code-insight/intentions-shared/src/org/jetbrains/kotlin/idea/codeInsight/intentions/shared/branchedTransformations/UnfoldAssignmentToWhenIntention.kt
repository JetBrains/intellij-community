// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.BranchedUnfoldingUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class UnfoldAssignmentToWhenIntention :
    KotlinApplicableModCommandAction<KtBinaryExpression, Unit>(KtBinaryExpression::class) {

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> {
        if (element.operationToken !in KtTokens.ALL_ASSIGNMENTS) return emptyList()
        if (element.left == null) return emptyList()
        val right = element.right as? KtWhenExpression ?: return emptyList()
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(right)) return emptyList()
        if (right.entries.any { it.expression == null }) return emptyList()
        return  listOf(TextRange(0, right.whenKeyword.endOffset - element.startOffset))
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.assignment.with.when.expression")

    override fun getPresentation(context: ActionContext, element: KtBinaryExpression): Presentation? =
        if (isElementApplicable(element, context)) {
            Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)
        } else {
            null
        }

    override fun invoke(actionContext: ActionContext, element: KtBinaryExpression, elementContext: Unit, updater: ModPsiUpdater) {
        BranchedUnfoldingUtils.unfoldAssignmentToWhen(element) {
            updater.moveCaretTo(it)
        }
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit = Unit
}