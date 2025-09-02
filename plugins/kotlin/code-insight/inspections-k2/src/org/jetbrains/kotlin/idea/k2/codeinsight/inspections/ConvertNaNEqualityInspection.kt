// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

private const val NAN_NAME = "NaN"
private const val REGULAR_PATTERN = "$0.isNaN()"
private const val INVERTED_PATTERN = "!$0.isNaN()"

internal class ConvertNaNEqualityInspection :
    KotlinApplicableInspectionBase.Simple<KtBinaryExpression, ConvertNaNEqualityInspection.Context>() {

    data class Context(
        val otherExpression: KtExpression,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = binaryExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("equality.check.with.nan.should.be.replaced.with.isnan")

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> =
        ApplicabilityRange.self(element)

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        val operationToken = element.operationToken
        return (operationToken == KtTokens.EQEQ || operationToken == KtTokens.EXCLEQ) &&
                (element.left?.text?.endsWith(NAN_NAME) == true || element.right?.text?.endsWith(NAN_NAME) == true)
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        val left = element.left ?: return null
        val right = element.right ?: return null

        val otherExpression = when {
            isNaNExpression(left) -> right
            isNaNExpression(right) -> left
            else -> return null
        }

        return Context(otherExpression)
    }

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("convert.na.n.equality.quick.fix.text")

        override fun applyFix(
            project: Project,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            val inverted = when (element.operationToken) {
                KtTokens.EXCLEQ -> true
                KtTokens.EQEQ -> false
                else -> return
            }

            val pattern = if (inverted) INVERTED_PATTERN else REGULAR_PATTERN
            element.replace(KtPsiFactory(project).createExpressionByPattern(pattern, context.otherExpression))
        }
    }
}

private fun KaSession.isNaNExpression(expression: KtExpression): Boolean {
    if (expression.text?.endsWith(NAN_NAME) != true) return false

    val symbol = expression.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return false
    val fqName = symbol.callableId?.asSingleFqName()?.asString() ?: return false

    return NaNSet.contains(fqName)
}

private val NaNSet = setOf(
    "kotlin.Double.Companion.NaN",
    "java.lang.Double.NaN",
    "kotlin.Float.Companion.NaN",
    "java.lang.Float.NaN",
)
