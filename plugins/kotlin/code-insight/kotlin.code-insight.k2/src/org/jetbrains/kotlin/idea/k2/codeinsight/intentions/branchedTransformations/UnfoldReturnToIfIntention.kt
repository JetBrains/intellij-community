// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class UnfoldReturnToIfIntention : KotlinApplicableModCommandAction<KtReturnExpression, Unit>(KtReturnExpression::class) {
    override fun invoke(
        actionContext: ActionContext,
        element: KtReturnExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val ifExpression = element.returnedExpression as KtIfExpression
        val thenExpr = ifExpression.then?.lastBlockStatementOrThis() ?: return
        val elseExpr = ifExpression.`else`?.lastBlockStatementOrThis()

        val newIfExpression = ifExpression.copied()
        val newThenExpr = newIfExpression.then?.lastBlockStatementOrThis() ?: return
        val newElseExpr = newIfExpression.`else`?.lastBlockStatementOrThis()

        val psiFactory = KtPsiFactory(element.project)
        val labelName = element.getLabelName()
        newThenExpr.replace(createReturnExpression(thenExpr, labelName, psiFactory))
        if (elseExpr != null) {
            newElseExpr?.replace(createReturnExpression(elseExpr, labelName, psiFactory))
        }
        element.replace(newIfExpression)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.return.with.if.expression")

    context(KaSession)
    override fun prepareContext(element: KtReturnExpression) {
    }

    override fun getApplicableRanges(element: KtReturnExpression): List<TextRange> {
        val ifExpression = element.returnedExpression as? KtIfExpression ?: return emptyList()
        if (ifExpression.then == null) return emptyList()
        return listOf(
            TextRange(0, ifExpression.ifKeyword.endOffset - element.startOffset)
        )
    }

    override fun isApplicableByPsi(element: KtReturnExpression): Boolean = element.returnedExpression is KtIfExpression

    private fun createReturnExpression(
        expr: KtExpression,
        labelName: String?,
        psiFactory: KtPsiFactory
    ): KtExpression {
        val label = labelName?.let { "@$it" }.orEmpty()
        val returnText = when (expr) {
            is KtBreakExpression, is KtContinueExpression, is KtReturnExpression, is KtThrowExpression -> ""
            else -> {
                analyze(expr) {
                    if (expr.expressionType?.isNothingType == true) {
                        ""
                    } else {
                        "return$label "
                    }
                }
            }
        }
        return psiFactory.createExpressionByPattern("$returnText$0", expr)
    }
}