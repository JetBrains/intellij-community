// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.EmptinessCheckFunctionUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class ReplaceNegatedIsEmptyWithIsNotEmptyInspection :
    KotlinApplicableInspectionBase.Simple<KtPrefixExpression, Pair<String, String>>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = prefixExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtPrefixExpression): Boolean =
        element.operationToken == KtTokens.EXCL

    override fun KaSession.prepareContext(element: KtPrefixExpression): Pair<String, String>? {
        val base = element.baseExpression?.let { KtPsiUtil.deparenthesize(it) } ?: return null
        val from = base.calleeText() ?: return null

        val to = EmptinessCheckFunctionUtils.invertFunctionCall(base)?.calleeText() ?: return null

        return from to to
    }

    override fun getProblemDescription(
        element: KtPrefixExpression,
        context: Pair<String, String>,
    ): String = KotlinBundle.message("replace.negated.0.with.1", context.first, context.second)

    override fun createQuickFix(
        element: KtPrefixExpression,
        context: Pair<String, String>,
    ): KotlinModCommandQuickFix<KtPrefixExpression> = ReplaceNegatedIsEmptyWithIsNotEmptyQuickFix(context.first, context.second)
}

private class ReplaceNegatedIsEmptyWithIsNotEmptyQuickFix(
    private val from: String,
    private val to: String,
) : KotlinModCommandQuickFix<KtPrefixExpression>() {

    override fun getFamilyName(): String = KotlinBundle.message("replace.negated.0.with.1", from, to)

    override fun applyFix(
        project: Project,
        element: KtPrefixExpression,
        updater: ModPsiUpdater,
    ) {
        val baseExpression = KtPsiUtil.deparenthesize(element.baseExpression) ?: return
        val psiFactory = KtPsiFactory(project)
        val newExpression = when (baseExpression) {
            is KtCallExpression -> psiFactory.createExpression("$to()")
            is KtQualifiedExpression -> psiFactory.createExpressionByPattern("$0.$to()", baseExpression.receiverExpression)
            else -> return
        }
        element.replace(newExpression)
    }
}

private fun KtExpression.calleeText(): String? {
    val call = when (this) {
        is KtQualifiedExpression -> selectorExpression as? KtCallExpression
        is KtCallExpression -> this
        else -> null
    } ?: return null
    return call.calleeExpression?.text
}
