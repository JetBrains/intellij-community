// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.contracts.description.KtContractCallsInPlaceContractEffectDeclaration
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange.AT_MOST_ONCE
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange.EXACTLY_ONCE
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddLoopLabelFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.util.match

/**
 * Affected tests:
 * [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SharedK1LocalInspectionTestGenerated.AmbiguousNonLocalJump]
 * [org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.SharedK2LocalInspectionTestGenerated.AmbiguousNonLocalJump]
 */
class AmbiguousNonLocalJumpInspection : AbstractKotlinInspection() {
    // The inspection only makes sense when BreakContinueInInlineLambdas feature is on
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        if (holder.file.languageVersionSettings.supportsFeature(LanguageFeature.BreakContinueInInlineLambdas)) MyVisitor(holder)
        else PsiElementVisitor.EMPTY_VISITOR
}

private class MyVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitBreakExpression(expression: KtBreakExpression) = visit(expression)
    override fun visitContinueExpression(expression: KtContinueExpression) = visit(expression)

    private fun visit(jump: KtExpressionWithLabel) {
        if (jump.getLabelName() != null) return // break or continue already has a label. It can't be ambiguous in this case
        val loop = jump.parents.filterIsInstance<KtLoopExpression>().firstOrNull() ?: return
        val loopKeyword = loop.loopKeyword ?: return
        val problematicCallExpr = findCallExprThatCausesUnlabeledNonLocalBreakOrContinueAmbiguity(jump)?.calleeExpression?.text ?: return
        holder.registerProblem(
            jump,
            KotlinBundle.message("ambiguous.non.local.break.or.continue", jump.text, loopKeyword, problematicCallExpr),
            AddLoopLabelFix(loop, jump)
        )
    }
}

private val KtLoopExpression.loopKeyword: String?
    get() = when (this) {
        is KtWhileExpression -> "while"
        is KtForExpression -> "for"
        is KtDoWhileExpression -> "do-while"
        else -> null
    }

private fun findCallExprThatCausesUnlabeledNonLocalBreakOrContinueAmbiguity(jump: KtExpressionWithLabel): KtCallExpression? = jump.parents
    .takeWhile { it !is KtLoopExpression }
    .mapNotNull { functionLiteral ->
        functionLiteral.findMatchingCallExpr()?.takeIf { doesCauseAmbiguityForUnlabeledNonLocalBreakOrContinue(it, functionLiteral) }
    }
    .firstOrNull()

private fun doesCauseAmbiguityForUnlabeledNonLocalBreakOrContinue(callExpr: KtCallExpression, functionLiteral: PsiElement): Boolean =
    true == analyze(callExpr) {
        callExpr.resolveCall().successfulCallOrNull<KtFunctionCall<*>>()?.argumentMapping?.get(functionLiteral)
            ?.takeIf(::isInlinedParameter)
            ?.name
            ?.let { lambdaParameterName ->
                (callExpr.calleeExpression as? KtReferenceExpression)?.mainReference?.resolveToSymbol()
                    ?.let { it as? KtFunctionSymbol }
                    ?.takeIf(KtFunctionSymbol::isInline)
                    ?.contractEffects
                    ?.none {
                        it is KtContractCallsInPlaceContractEffectDeclaration &&
                                (it.valueParameterReference.parameterSymbol as? KtValueParameterSymbol)?.name == lambdaParameterName &&
                                it.occurrencesRange in setOf(AT_MOST_ONCE, EXACTLY_ONCE)
                    }
            }
    }

private fun PsiElement.findMatchingCallExpr(): KtCallExpression? =
    parentsWithSelf.match(KtLambdaExpression::class, KtValueArgument::class, KtValueArgumentList::class, last = KtCallExpression::class)
        ?: parentsWithSelf.match(KtLambdaExpression::class, KtLambdaArgument::class, last = KtCallExpression::class)
        ?: parentsWithSelf.match(KtNamedFunction::class, KtValueArgument::class, KtValueArgumentList::class, last = KtCallExpression::class)

private fun isInlinedParameter(parameter: KtVariableLikeSignature<KtValueParameterSymbol>): Boolean =
    parameter.symbol.run { !isCrossinline && !isNoinline }
