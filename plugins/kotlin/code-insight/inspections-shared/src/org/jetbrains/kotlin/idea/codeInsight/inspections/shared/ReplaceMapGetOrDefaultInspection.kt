// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class ReplaceMapGetOrDefaultInspection :
    KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, ReplaceMapGetOrDefaultInspection.Context>() {

    internal class Context(val receiver: KtExpression, val firstArg: KtExpression, val secondArg: KtExpression)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitorVoid = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Context
    ): @InspectionMessage String =
        KotlinBundle.message("replace.with.0.1.2", context.receiver.text, context.firstArg.text, context.secondArg.text)

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = ReplaceMapGetOrDefaultFix()

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> {
        return ApplicabilityRange.single(element) { it.callExpression?.calleeExpression }
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val callExpression = element.callExpression ?: return false
        val calleeExpression = callExpression.calleeExpression ?: return false
        if (calleeExpression.text != getOrDefaultFqName.shortName().asString()) return false
        return callExpression.arguments() != null
    }

    private fun KaSession.isApplicableByAnalyze(callExpression: KtCallExpression, receiverExpression: KtExpression): Boolean {
        val call = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        if (call.symbol.getFqNameIfPackageOrNonLocal() != getOrDefaultFqName) return false
        val receiverType = receiverExpression.expressionType as? KaClassType ?: return false
        val lastTypeArgument = receiverType.typeArguments.lastOrNull() ?: return false
        return lastTypeArgument.type?.nullability?.isNullable != true
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
        val callExpression = element.callExpression ?: return null
        val receiverExpression = element.receiverExpression
        if (!isApplicableByAnalyze(callExpression, receiverExpression)) return null

        val (firstArg, secondArg) = callExpression.arguments() ?: return null
        return Context(receiverExpression, firstArg, secondArg)
    }
}

private class ReplaceMapGetOrDefaultFix : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {

    override fun applyFix(
        project: Project,
        element: KtDotQualifiedExpression,
        updater: ModPsiUpdater
    ) {
        val callExpression = element.callExpression ?: return
        val (firstArg, secondArg) = callExpression.arguments() ?: return
        val replaced = element.replaced(
            KtPsiFactory(element.project).createExpressionByPattern("$0[$1] ?: $2", element.receiverExpression, firstArg, secondArg)
        )

        replaced.findDescendantOfType<KtArrayAccessExpression>()?.leftBracket?.startOffset?.let {
            updater.moveCaretTo(it)
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.indexing.and.elvis.operator")
}

private fun KtCallExpression.arguments(): Pair<KtExpression, KtExpression>? {
    val args = valueArguments
    if (args.size != 2) return null
    val first = args[0].getArgumentExpression() ?: return null
    val second = args[1].getArgumentExpression() ?: return null
    return first to second
}

private val getOrDefaultFqName: FqName = FqName("kotlin.collections.Map.getOrDefault")
