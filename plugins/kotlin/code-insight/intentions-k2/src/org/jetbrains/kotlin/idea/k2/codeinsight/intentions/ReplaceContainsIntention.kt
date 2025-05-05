// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.appendSemicolonBeforeLambdaContainingElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.OperatorNameConventions

class ReplaceContainsIntention : KotlinApplicableModCommandAction<KtDotQualifiedExpression, Unit>(KtDotQualifiedExpression::class) {

    override fun getPresentation(
        context: ActionContext,
        element: KtDotQualifiedExpression
    ): Presentation {
        return Presentation.of(familyName).withPriority(PriorityAction.Priority.HIGH)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.contains.call.with.in.operator")

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> {
        val selectorExpression = element.selectorExpression as? KtCallExpression ?: return emptyList()
        val calleeExpression = selectorExpression.calleeExpression ?: return emptyList()
        return listOf(calleeExpression.textRange.shiftLeft(element.startOffset))
    }

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

    override fun invoke(
        actionContext: ActionContext,
        element: KtDotQualifiedExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
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

