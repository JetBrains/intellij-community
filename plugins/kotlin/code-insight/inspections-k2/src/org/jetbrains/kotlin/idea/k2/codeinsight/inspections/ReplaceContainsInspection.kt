// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.appendSemicolonBeforeLambdaContainingElement
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.OperatorNameConventions

class ReplaceContainsInspection :
    KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, Unit>(),
    CleanupLocalInspectionTool {

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit
    ): @InspectionMessage String =
        KotlinBundle.message("replace.contains.call.with.in.operator.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.callExpression?.calleeExpression }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val selectorExpression = element.selectorExpression as? KtCallExpression ?: return false
        val calleeExpression = selectorExpression.calleeExpression ?: return false
        if (element.receiverExpression is KtSuperExpression) return false
        return calleeExpression.text == OperatorNameConventions.CONTAINS.asString() &&
                selectorExpression.valueArguments.size == 1
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        val selectorExpression = element.selectorExpression as? KtCallExpression ?: return null
        val resolvedCall = selectorExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val symbol = resolvedCall.symbol as? KaNamedFunctionSymbol ?: return null

        if (!symbol.isOperator) return null
        if (!symbol.returnType.isBooleanType) return null

        val valueArgument = selectorExpression.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
        val variableSignature = resolvedCall.argumentMapping[valueArgument] ?: return null
        if (symbol.valueParameters.indexOf(variableSignature.symbol) != 0) return null

        // Check if the receiver expression has a value
        return (element.receiverExpression.expressionType != null).asUnit
    }

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.contains.call.with.in.operator")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val selectorExpression = element.selectorExpression as? KtCallExpression ?: return
            val argument = selectorExpression.valueArguments.singleOrNull()?.getArgumentExpression() ?: return
            val receiver = element.receiverExpression

            val psiFactory = KtPsiFactory(element.project)

            val prefixExpression = element.parent as? KtPrefixExpression
            val expression = if (prefixExpression != null && prefixExpression.operationToken == KtTokens.EXCL) {
                prefixExpression.replace(psiFactory.createExpressionByPattern("$0 !in $1", argument, receiver))
            } else {
                element.replace(psiFactory.createExpressionByPattern("$0 in $1", argument, receiver))
            }

            // Append semicolon to previous statement if needed
            if (argument is KtLambdaExpression) {
                psiFactory.appendSemicolonBeforeLambdaContainingElement(expression)
            }
        }
    }
}

