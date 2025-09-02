// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.*

class ExpandBooleanExpressionIntention : KotlinApplicableModCommandAction<KtExpression, Unit>(KtExpression::class) {
    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val ifExpression = KtPsiFactory(element.project).createExpressionByPattern("if ($0) {\ntrue\n} else {\nfalse\n}", element)
        val replaced = element.replace(ifExpression)
        replaced?.let { updater.moveCaretTo(it) }
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("expand.boolean.expression.to.if.else")

    override fun KaSession.prepareContext(element: KtExpression): Unit? {
        if (!element.isTargetExpression() || element.parent.isTargetExpression()) return null
        if (KtPsiUtil.safeDeparenthesize(element) is KtConstantExpression) return null

        val parent = element.parent
        if (parent is KtValueArgument || parent is KtParameter || parent is KtStringTemplateEntry) return null

        if (element.expressionType?.isBooleanType != true) return null

        return Unit
    }

    private fun PsiElement.isTargetExpression() = this is KtSimpleNameExpression || this is KtCallExpression ||
            this is KtQualifiedExpression || this is KtOperationExpression || this is KtParenthesizedExpression
}