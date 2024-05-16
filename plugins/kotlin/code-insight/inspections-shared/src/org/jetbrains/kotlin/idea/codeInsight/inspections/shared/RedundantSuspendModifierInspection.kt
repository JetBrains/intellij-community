// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallInfo
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtKotlinPropertySymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.codeInsight.CallTarget
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallTargetProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.processExpressionsRecursively
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.namedFunctionVisitor

internal class RedundantSuspendModifierInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return namedFunctionVisitor(fun(function) {
            val suspendModifier = function.modifierList?.getModifier(KtTokens.SUSPEND_KEYWORD) ?: return
            if (!function.hasBody()) return
            if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) || function.hasModifier(KtTokens.ACTUAL_KEYWORD)) return

            analyze(function) {
                val functionSymbol = function.getFunctionLikeSymbol() as? KtFunctionSymbol ?: return
                if (functionSymbol.modality == Modality.OPEN) return

                if (function.hasSuspendOrUnresolvedCall()) return

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
        if (this is KtKotlinPropertySymbol && getFqNameIfPackageOrNonLocal() == coroutineContextFqName) {
            return true
        }
        return this is KtFunctionSymbol && isSuspend
    }

    private fun KtNamedFunction.hasSuspendOrUnresolvedCall(): Boolean {
        var hasSuspendOrUnresolvedCall = false
        val containingFunction = this

        KotlinCallProcessor.processExpressionsRecursively(this, object : KotlinCallTargetProcessor {
            override fun KtAnalysisSession.processCallTarget(target: CallTarget): Boolean {
                if (target.symbol.isSuspendSymbol() && target.symbol.psi != containingFunction) {
                    hasSuspendOrUnresolvedCall = true
                    return false
                }
                return true
            }

            override fun KtAnalysisSession.processUnresolvedCall(element: KtElement, callInfo: KtCallInfo?): Boolean {
                if (callInfo != null) {
                    // A callInfo of null means that the element could not be resolved at all, so it is not even unresolved.
                    // For example, if the element is not an expression, so we ignore those cases.
                    hasSuspendOrUnresolvedCall = true
                    return false
                }
                return true
            }
        })

        return hasSuspendOrUnresolvedCall
    }
}