// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.getParentLambdaLabelName
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal class AddLabeledReturnInLambdaIntention : KotlinApplicableModCommandAction<KtBlockExpression, Unit>(KtBlockExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.labeled.return.to.last.expression.in.a.lambda")

    override fun getPresentation(
        context: ActionContext,
        element: KtBlockExpression,
    ): Presentation? {
        val labelName = element.getParentLambdaLabelName()?.takeIf {
            it != KtTokens.SUSPEND_KEYWORD.value
        } ?: return null
        val actionName = KotlinBundle.message("add.return.at.0", labelName)
        return Presentation.of(actionName).withPriority(PriorityAction.Priority.LOW)
    }

    override fun isApplicableByPsi(
        element: KtBlockExpression
    ): Boolean =
        element.getNonStrictParentOfType<KtLambdaExpression>() != null

    override fun getApplicableRanges(
        element: KtBlockExpression
    ): List<TextRange> =
        listOfNotNull(element.statements.lastOrNull()?.textRangeIn(element))

    override fun KaSession.prepareContext(
        element: KtBlockExpression
    ): Unit? =
        element.statements.lastOrNull().takeIf { it !is KtReturnExpression }?.isUsedAsExpression?.asUnit

    override fun invoke(
        actionContext: ActionContext,
        element: KtBlockExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val labelName = element.getParentLambdaLabelName() ?: return
        val lastStatement = element.statements.lastOrNull() ?: return
        val newExpression = KtPsiFactory(element.project).createExpressionByPattern("return@$labelName $0", lastStatement)
        lastStatement.replace(newExpression)
    }
}
