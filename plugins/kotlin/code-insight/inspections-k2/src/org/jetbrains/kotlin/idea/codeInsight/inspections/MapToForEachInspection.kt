// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInsight.intention.PriorityAction
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
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSymbol
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticCheckerKind
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.importableFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.util.isUnitLiteral
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExperimentalApi
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

private const val FOR_EACH_INDEXED_FUNCTION_NAME: String = "forEachIndexed"
private val MAP_FQ_NAMES = setOf(
    StandardKotlinNames.Collections.map,
    StandardKotlinNames.Collections.mapIndexed,
    StandardKotlinNames.Collections.mapNotNull,
)

internal class MapToForEachInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, MapToForEachInspection.Context>() {

    data class Context(
        val returns: List<SmartPsiElementPointer<KtReturnExpression>>,
        val replacementName: String,
        val isReturnValueNotUsed: Boolean,
    )

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
        if (MAP_FQ_NAMES.none { fqName ->
                calleeText == fqName.shortName().asString() || element.containingKtFile.importDirectives.any {
                    it.importedFqName == fqName && calleeText == it.aliasName
                }
            }) return false

        val statementCandidate = element.getQualifiedExpressionForSelectorOrThis()
        if (!KtPsiUtil.isStatement(statementCandidate)) return false

        return element.valueArguments.size == 1
    }

    @OptIn(KaExperimentalApi::class)
    context(session: KaSession)
    override fun prepareContext(element: KtCallExpression): Context? {
        val whole = element.getQualifiedExpressionForSelectorOrThis()
        if (whole.isUsedAsExpression) return null

        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val functionSymbol = resolvedCall.symbol

        val replacementName = when (functionSymbol.importableFqName) {
            StandardKotlinNames.Collections.map,
            StandardKotlinNames.Collections.mapNotNull -> StandardKotlinNames.For.forEachName.identifier
            StandardKotlinNames.Collections.mapIndexed -> FOR_EACH_INDEXED_FUNCTION_NAME
            else -> return null
        }
        if (functionSymbol.typeParameters.size != 2) return null
        if (resolvedCall.typeArgumentsMapping.size != 2) return null

        val labeledReturnExpressions = collectReturns(element) ?: return null
        val isReturnValueNotUsed = whole.diagnostics()
            .withCheckers(KaDiagnosticCheckerKind.ALL)
            .any { it is KaFirDiagnostic.ReturnValueNotUsed }

        return Context(labeledReturnExpressions, replacementName, isReturnValueNotUsed)
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = if (context.isReturnValueNotUsed) {
        HighPriorityReplaceWithForEachFix(context)
    } else {
        NormalPriorityReplaceWithForEachFix(context)
    }

    private sealed class ReplaceWithForEachFix(
        private val context: Context,
    ) : KotlinModCommandQuickFix<KtCallExpression>(), PriorityAction {

        abstract override fun getPriority(): PriorityAction.Priority

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.0", context.replacementName)

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val callee = element.calleeExpression as? KtNameReferenceExpression ?: return

            val writableReturns = context.returns.mapNotNull { updater.getWritable(it.element) }

            val psiFactory = KtPsiFactory(project)
            callee.replace(psiFactory.createSimpleName(context.replacementName))

            element.typeArgumentList?.delete()

            writableReturns.forEach {
                val dummyReturnExpr = psiFactory.createExpressionByPattern(
                    "${KtTokens.RETURN_KEYWORD}@${context.replacementName}"
                ) as KtReturnExpression
                val newTargetLabel = dummyReturnExpr.getTargetLabel()!!

                it.getTargetLabel()?.replace(newTargetLabel)
            }
        }
    }

    private class NormalPriorityReplaceWithForEachFix(context: Context) : ReplaceWithForEachFix(context) {
        override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL
    }

    private class HighPriorityReplaceWithForEachFix(context: Context) : ReplaceWithForEachFix(context) {
        override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH
    }
}

@OptIn(KaExperimentalApi::class, KtExperimentalApi::class)
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
            val targetsMap = returnExpr.getTargetLabel()?.resolveSymbol()?.psi == functionLike
            if (!targetsMap) continue

            if (returnExpr.returnedExpression?.isUnitLiteral() == false) return null
            add(returnExpr.createSmartPointer())
        }
    }
}
