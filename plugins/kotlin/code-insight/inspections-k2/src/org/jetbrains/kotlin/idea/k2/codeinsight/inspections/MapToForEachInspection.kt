// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.util.isUnitLiteral
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

private const val FOR_EACH_FUNCTION_NAME: String = "forEach"

internal class MapToForEachInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, MapToForEachInspection.Context>() {

    @JvmInline
    value class Context(val returns: List<SmartPsiElementPointer<KtReturnExpression>>)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.map.can.be.replaced.with.for.each.warning", element.calleeExpression?.text ?: "")

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        // Skip in debugger's Evaluate Expression where the expression result is shown.
        if (element.containingKtFile is KtCodeFragment) return false

        val calleeText = element.calleeExpression?.text ?: return false
        val mapFqName = StandardKotlinNames.Collections.map
        if (calleeText != mapFqName.shortName().asString() && element.containingKtFile.importDirectives.none {
                it.importedFqName == mapFqName && calleeText == it.aliasName
            }) return false

        val statementCandidate = element.getQualifiedExpressionForSelectorOrThis()
        if (!KtPsiUtil.isStatement(statementCandidate)) return false

        return element.valueArguments.size == 1
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val whole = element.getQualifiedExpressionForSelectorOrThis()
        if (whole.isUsedAsExpression) return null

        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val functionSymbol = resolvedCall.symbol

        if (functionSymbol.importableFqName != StandardKotlinNames.Collections.map) return null
        if (functionSymbol.typeParameters.size != 2) return null
        if (resolvedCall.typeArgumentsMapping.size != 2) return null

        val labeledReturnExpressions = collectReturns(element) ?: return null

        return Context(labeledReturnExpressions)
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.0", FOR_EACH_FUNCTION_NAME)

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val callee = element.calleeExpression as? KtNameReferenceExpression ?: return

            val writableReturns = context.returns.mapNotNull { updater.getWritable(it.element) }

            val psiFactory = KtPsiFactory(project)
            callee.replace(psiFactory.createSimpleName(FOR_EACH_FUNCTION_NAME))

            element.typeArgumentList?.delete()

            writableReturns.forEach {
                val dummyReturnExpr = psiFactory.createExpressionByPattern(
                    "${KtTokens.RETURN_KEYWORD}@$FOR_EACH_FUNCTION_NAME"
                ) as KtReturnExpression
                val newTargetLabel = dummyReturnExpr.getTargetLabel()!!

                it.getTargetLabel()?.replace(newTargetLabel)
            }
        }
    }
}

context(_: KaSession)
private fun collectReturns(
    element: KtCallExpression,
): List<SmartPsiElementPointer<KtReturnExpression>>? {
    val valueArgument = element.valueArguments.singleOrNull() ?: return null
    val functionLike = when (val expr = valueArgument.getArgumentExpression()) {
        is KtLambdaExpression -> expr.functionLiteral
        is KtNamedFunction -> expr
        else -> return emptyList()
    }

    return buildList {
        for (returnExpr in functionLike.collectDescendantsOfType<KtReturnExpression>()) {
            val targetsMap = returnExpr.getTargetLabel()?.mainReference?.resolveToSymbol()?.psi == functionLike
            if (!targetsMap) continue

            if (returnExpr.returnedExpression?.isUnitLiteral() == false) return null
            add(returnExpr.createSmartPointer())
        }
    }
}
