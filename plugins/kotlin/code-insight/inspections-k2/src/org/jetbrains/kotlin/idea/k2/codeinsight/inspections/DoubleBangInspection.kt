// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.postfixExpressionVisitor

internal class DoubleBangInspection :
    KotlinApplicableInspectionBase.Simple<KtPostfixExpression, String>(),
    CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = postfixExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtPostfixExpression): Boolean =
        element.operationToken == KtTokens.EXCLEXCL && element.baseExpression != null

    override fun getApplicableRanges(element: KtPostfixExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.operationReference }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtPostfixExpression): String? {
        if (element.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).any {
                it is KaFirDiagnostic.UnnecessaryNotNullAssertion
            }
        ) return null

        val expression = element.baseExpression ?: return null
        val baseExpression = expression.deparenthesize(true)
        return "'${formatForUseInErrorArgument(baseExpression.text)}' must not be null"
    }

    override fun getProblemDescription(
        element: KtPostfixExpression,
        context: String,
    ): @InspectionMessage String = KotlinBundle.message("inspection.double.bang.display.name")

    override fun createQuickFix(
        element: KtPostfixExpression,
        context: String,
    ): KotlinModCommandQuickFix<KtPostfixExpression> = object : KotlinModCommandQuickFix<KtPostfixExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.error")

        override fun applyFix(
            project: Project,
            element: KtPostfixExpression,
            updater: ModPsiUpdater,
        ) {
            val writableElement = updater.getWritable(element)
            val expression = writableElement.baseExpression ?: return
            val baseExpression = expression.deparenthesize(true)
            val psiFactory = KtPsiFactory(project)
            val messageExpression = psiFactory.createExpression("\"${StringUtil.escapeStringCharacters(context)}\"")
            val replacedExpression = writableElement.replaced<KtExpression>(
                psiFactory.createExpressionByPattern("($0 ?: kotlin.error($1))", baseExpression, messageExpression)
            )

            val binaryExpression = when (replacedExpression) {
                is KtBinaryExpression -> replacedExpression
                is KtParenthesizedExpression -> replacedExpression.expression as? KtBinaryExpression
                else -> null
            } ?: return

            shortenReferences(binaryExpression.right as KtElement)

            val parenthesizedExpression = replacedExpression as? KtParenthesizedExpression ?: return
            if (KtPsiUtil.areParenthesesUseless(parenthesizedExpression)) {
                val innerExpression = parenthesizedExpression.expression ?: return
                parenthesizedExpression.replace(innerExpression)
            }
        }
    }

    private fun KtExpression.deparenthesize(keepAnnotations: Boolean): KtExpression {
        var deparenthesized: KtExpression = this
        while (true) {
            val baseExpression =
                KtPsiUtil.deparenthesizeOnce(deparenthesized, keepAnnotations)
                    ?: return deparenthesized

            if (baseExpression is KtAnnotatedExpression)
                return deparenthesized

            if (baseExpression === deparenthesized) return baseExpression
            deparenthesized = baseExpression
        }
    }

    private fun formatForUseInErrorArgument(expressionText: String): String {
        val lines = expressionText.split('\n')
        return if (lines.size > 1) {
            lines.first().trim() + " " + Typography.ellipsis.toString()
        } else {
            expressionText.trim()
        }
    }
}
