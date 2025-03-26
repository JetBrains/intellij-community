// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.contracts.description.KaContractCallsInPlaceContractEffectDeclaration
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange.AT_MOST_ONCE
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange.EXACTLY_ONCE
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddLoopLabelFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.util.match

/**
 * Affected tests:
 * [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SharedK1LocalInspectionTestGenerated.AmbiguousNonLocalJump]
 * [org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.SharedK2LocalInspectionTestGenerated.AmbiguousNonLocalJump]
 */
internal class AmbiguousNonLocalJumpInspection : AbstractKotlinInspection() {
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
        val ambiguousCallInfo = findCallExprThatCausesUnlabeledNonLocalBreakOrContinueAmbiguity(jump) ?: return
        val problematicCallExpr = ambiguousCallInfo.calleeExpression.text

        val description = if (ambiguousCallInfo.isDeclaredInSourceModule)
            KotlinBundle.message("ambiguous.non.local.break.or.continue.use.label.or.contract", jump.text, loopKeyword, problematicCallExpr, problematicCallExpr)
        else KotlinBundle.message("ambiguous.non.local.break.or.continue.use.label", jump.text, loopKeyword, problematicCallExpr)

        holder.registerProblem(
            jump,
            description,
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

private class AmbiguousCallInfo(
    val calleeExpression: KtExpression,
    val isDeclaredInSourceModule: Boolean,
)

private fun findCallExprThatCausesUnlabeledNonLocalBreakOrContinueAmbiguity(jump: KtExpressionWithLabel): AmbiguousCallInfo? = jump.parents
    .takeWhile { it !is KtLoopExpression }
    .mapNotNull { checkAmbiguityForUnlabeledNonLocalBreakOrContinue(it) }
    .firstOrNull()

private fun checkAmbiguityForUnlabeledNonLocalBreakOrContinue(functionLiteral: PsiElement): AmbiguousCallInfo? {
    val callExpression = functionLiteral.findMatchingCallExpr() ?: return null
    analyze(callExpression) {
        val successfulCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val calleeExpression = callExpression.calleeExpression as? KtReferenceExpression ?: return null
        val lambdaParamName = successfulCall.argumentMapping[functionLiteral]?.takeIf(::isInlinedParameter)?.name ?: return null
        val calleeExpressionSymbol = calleeExpression.mainReference.resolveToSymbol()
            ?.let { it as? KaNamedFunctionSymbol }
            ?.takeIf(KaNamedFunctionSymbol::isInline) ?: return null
        if (calleeExpressionSymbol.hasNoCallsInPlaceContract(lambdaParamName)) {
            val isDeclaredInSourceModule = calleeExpressionSymbol.containingModule is KaSourceModule
            return AmbiguousCallInfo(calleeExpression, isDeclaredInSourceModule)
        }
    }
    return null
}

@OptIn(KaExperimentalApi::class)
private fun KaNamedFunctionSymbol.hasNoCallsInPlaceContract(lambdaParameterName: Name): Boolean =
    contractEffects.none {
        it is KaContractCallsInPlaceContractEffectDeclaration
                && (it.valueParameterReference.symbol as? KaValueParameterSymbol)?.name == lambdaParameterName
                && it.occurrencesRange in setOf(AT_MOST_ONCE, EXACTLY_ONCE)
    }

private fun PsiElement.findMatchingCallExpr(): KtCallExpression? =
    parentsWithSelf.match(KtLambdaExpression::class, KtValueArgument::class, KtValueArgumentList::class, last = KtCallExpression::class)
        ?: parentsWithSelf.match(KtLambdaExpression::class, KtLambdaArgument::class, last = KtCallExpression::class)
        ?: parentsWithSelf.match(KtNamedFunction::class, KtValueArgument::class, KtValueArgumentList::class, last = KtCallExpression::class)

private fun isInlinedParameter(parameter: KaVariableSignature<KaValueParameterSymbol>): Boolean =
    parameter.symbol.run { !isCrossinline && !isNoinline }
