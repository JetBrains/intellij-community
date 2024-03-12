// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

internal class RedundantSuspendModifierInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return namedFunctionVisitor(fun(function) {
            if (!function.languageVersionSettings.supportsFeature(LanguageFeature.Coroutines)) return

            val suspendModifier = function.modifierList?.getModifier(KtTokens.SUSPEND_KEYWORD) ?: return
            if (!function.hasBody()) return
            if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) || function.hasModifier(KtTokens.ACTUAL_KEYWORD)) return

            analyze(function) {
                val functionSymbol = function.getFunctionLikeSymbol() as? KtFunctionSymbol ?: return
                if (functionSymbol.modality == Modality.OPEN) return

                if (function.hasSuspendOrUnresolvedCall(functionSymbol)) return

                holder.registerProblem(
                    suspendModifier, KotlinBundle.message("redundant.suspend.modifier"), IntentionWrapper(
                        RemoveModifierFixBase(function, KtTokens.SUSPEND_KEYWORD, isRedundant = true).asIntention()
                    )
                )
            }
        })
    }

    private val coroutineContextFqName = StandardNames.COROUTINES_PACKAGE_FQ_NAME.child(Name.identifier("coroutineContext"))

    context(KtAnalysisSession)
    private fun KtCallableSymbol.isSuspendSymbol(): Boolean {

        // Currently, Kotlin does not support suspending properties except for accessing the coroutineContext
        if (getFqNameIfPackageOrNonLocal() == coroutineContextFqName) {
            return true
        }

        return this is KtFunctionSymbol && isSuspend
    }

    context(KtAnalysisSession)
    private fun KtExpression.resolveMemberFunction(
        name: String,
        psiFactory: KtPsiFactory,
        context: KtExpression = this
    ): KtFunctionCall<*>? {
        val newExpression = psiFactory.createExpressionByPattern("$0.$1()", this, name)
        val fragment = KtPsiFactory(project).createExpressionCodeFragment(newExpression.text, context)
        val expression = fragment.firstChild as? KtExpression ?: return null
        return expression.resolveCall()?.successfulFunctionCallOrNull()
    }

    context(KtAnalysisSession)
    private fun KtForExpression.isSuspendingLoopOrUnresolved(): Boolean {
        val loopRangeExpression = loopRange ?: return true
        val psiFactory = KtPsiFactory(project)
        val iteratorFunction = loopRangeExpression.resolveMemberFunction("iterator", psiFactory) ?: return true
        if (iteratorFunction.partiallyAppliedSymbol.symbol.isSuspendSymbol()) {
            return true
        }
        val functionsToCheck = listOf("hasNext", "next")
        for (f in functionsToCheck) {
            val iteratorExpression = psiFactory.createExpressionByPattern("$0.iterator()", loopRangeExpression)
            val resolvedFunction = iteratorExpression.resolveMemberFunction(f, psiFactory, loopRangeExpression) ?: return true
            if (resolvedFunction.partiallyAppliedSymbol.symbol.isSuspendSymbol()) {
                return true
            }
        }
        return false
    }

    context(KtAnalysisSession)
    private fun KtCallInfo.isExternalSuspendOrUnresolved(selfSymbol: KtFunctionSymbol): Boolean {
        val functionCall = successfulCallOrNull<KtCallableMemberCall<*, *>>() ?: return true
        val symbol = functionCall.partiallyAppliedSymbol.symbol // Recursive call to itself, ignore
        if (symbol == selfSymbol) return false
        if (symbol.isSuspendSymbol()) return true

        return if (functionCall is KtCompoundVariableAccessCall) {
            val compoundAccessSymbol = functionCall.compoundAccess.operationPartiallyAppliedSymbol.symbol
            if (compoundAccessSymbol == selfSymbol) return false
            compoundAccessSymbol.isSuspendSymbol()
        } else {
            false
        }
    }

    context(KtAnalysisSession)
    private fun KtNamedFunction.hasSuspendOrUnresolvedCall(functionSymbol: KtFunctionSymbol): Boolean {
        return anyDescendantOfType<KtExpression> { expression ->
            if (expression == this) return@anyDescendantOfType false
            if (expression is KtForExpression) {
                return@anyDescendantOfType expression.isSuspendingLoopOrUnresolved()
            }
            // If resolveCall returns null, we skip it (likely block/function/etc., not an actual expression we want to analyze)
            val resolvedCall = expression.resolveCall()
                ?: return@anyDescendantOfType false
            // If we cannot resolve to anything or a singular call, then we do not know if this might be suspending or not
            if (resolvedCall is KtErrorCallInfo) {
                return@anyDescendantOfType true
            }
            resolvedCall.isExternalSuspendOrUnresolved(functionSymbol)
        }
    }
}