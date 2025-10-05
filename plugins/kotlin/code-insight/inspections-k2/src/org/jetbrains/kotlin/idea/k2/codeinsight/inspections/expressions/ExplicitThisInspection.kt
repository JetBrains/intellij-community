// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.components.collectPossibleReferenceShortenings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.k2.refactoring.util.ConvertReferenceToLambdaUtil
import org.jetbrains.kotlin.psi.*

internal class ExplicitThisInspection : KotlinApplicableInspectionBase.Simple<KtThisExpression, Unit>() {
    override fun getProblemDescription(element: KtThisExpression, context: Unit): String =
        KotlinBundle.message("redundant.explicit.this")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitThisExpression(expression: KtThisExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtThisExpression): Boolean {
        val parent = element.parent
        return parent is KtDotQualifiedExpression && parent.receiverExpression == element ||
                parent is KtCallableReferenceExpression && parent.receiverExpression == element
    }

    context(_: KaSession)
    private fun checkShortening(el: KtElement): Unit? {
        val shortenCommand = collectPossibleReferenceShortenings(
            el.containingKtFile,
            el.parent.textRange,
            shortenOptions = ShortenOptions.ALL_ENABLED
        )
        val hasShortening = shortenCommand.listOfQualifierToShortenInfo.any { it.qualifierToShorten.element?.receiverExpression == el }
        return hasShortening.asUnit
    }

    override fun KaSession.prepareContext(element: KtThisExpression): Unit? {
        val parent = element.parent ?: return null

        if (parent is KtCallableReferenceExpression) {
            val lambdaExpressionText = ConvertReferenceToLambdaUtil.prepareLambdaExpressionText(parent) ?: return null
            val lambdaCodeFragment = KtPsiFactory(element.project).createExpressionCodeFragment(lambdaExpressionText, element)
            val lambdaExpression = lambdaCodeFragment.getContentElement() as? KtLambdaExpression
            val lambdaBodyStatements = lambdaExpression?.bodyExpression?.statements
            val expression = lambdaBodyStatements?.firstOrNull() as? KtDotQualifiedExpression ?: return null
            return analyze(expression) {
                checkShortening(expression.receiverExpression)
            }
        }

        return checkShortening(element)
    }

    override fun createQuickFix(
        element: KtThisExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtThisExpression> = ExplicitThisExpressionFix(element.text)
}

internal class ExplicitThisExpressionFix(private val text: String) : KotlinModCommandQuickFix<KtThisExpression>() {
    override fun getFamilyName(): String = KotlinBundle.message("explicit.this.expression.fix.family.name", text)

    override fun applyFix(
        project: Project,
        thisExpression: KtThisExpression,
        updater: ModPsiUpdater
    ) {
        when (val parent = thisExpression.parent) {
            is KtDotQualifiedExpression -> parent.replace(parent.selectorExpression ?: return)
            is KtCallableReferenceExpression -> thisExpression.delete()
        }
    }
}

