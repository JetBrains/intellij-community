// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal class ConvertVariableAssignmentToExpressionIntention : KotlinApplicableModCommandAction<KtBinaryExpression, Unit>(
    KtBinaryExpression::class
) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.assignment.expression")

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.operationToken == KtTokens.EQ

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit = Unit

    override fun invoke(
        actionContext: ActionContext,
        element: KtBinaryExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val left = element.left ?: return
        val right = element.right ?: return
        val newElement = KtPsiFactory(element.project).createExpressionByPattern("$0.also { $1 = it }", right, left)
        element.replace(newElement)
    }
}
