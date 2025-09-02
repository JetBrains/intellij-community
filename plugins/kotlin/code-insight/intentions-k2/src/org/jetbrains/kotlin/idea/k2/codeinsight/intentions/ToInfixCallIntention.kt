// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class ToInfixCallIntention : KotlinApplicableModCommandAction<KtCallExpression, Unit>(KtCallExpression::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.infix.function.call")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.calleeExpression !is KtNameReferenceExpression) return false
        if (element.getQualifiedExpressionForSelector() !is KtDotQualifiedExpression) return false
        if (element.typeArgumentList != null) return false

        return hasSingleNonNamedArgument(element)
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val dotQualified = element.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression ?: return null

        val functionSymbol = getFunctionSymbol(element) ?: return null
        if (!functionSymbol.isInfix) return null

        // Check that the receiver is not a package
        val receiverIsNotPackage = (dotQualified.receiverExpression.resolveExpression() !is KaPackageSymbol)

        return receiverIsNotPackage.asUnit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val dotQualified = element.getQualifiedExpressionForSelector() as KtDotQualifiedExpression
        val receiver = dotQualified.receiverExpression
        val argument = element.valueArguments.single().getArgumentExpression()!!
        val functionName = element.calleeExpression!!.text

        val psiFactory = KtPsiFactory(element.project)
        val infixCall = psiFactory.createExpressionByPattern("$0 $functionName $1", receiver, argument)

        dotQualified.replace(infixCall)
    }
}

private fun hasSingleNonNamedArgument(element: KtCallExpression): Boolean {
    val argument = element.valueArguments.singleOrNull() ?: return false
    if (argument.isNamed()) return false
    return argument.getArgumentExpression() != null
}

private fun KaSession.getFunctionSymbol(element: KtCallExpression): KaNamedFunctionSymbol? {
    val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
    return call.symbol as? KaNamedFunctionSymbol
}
