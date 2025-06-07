// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.descendants
import com.intellij.psi.util.findParentOfType
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.CallTarget
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallTargetProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.process
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class RedundantSuspendModifierInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return namedFunctionVisitor(fun(function) {
            val suspendModifier = function.modifierList?.getModifier(KtTokens.SUSPEND_KEYWORD) ?: return
            if (!function.hasBody()) return
            if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) || function.hasModifier(KtTokens.ACTUAL_KEYWORD)) return

            analyze(function) {
                val functionSymbol = function.symbol as? KaNamedFunctionSymbol ?: return
                if (functionSymbol.modality == KaSymbolModality.OPEN) return

                if (!function.isSuspendModifierRedundant()) return

                holder.registerProblem(
                    suspendModifier,
                    KotlinBundle.message("redundant.suspend.modifier"),
                    RemoveModifierFixBase(function, KtTokens.SUSPEND_KEYWORD, isRedundant = true).asQuickFix(),
                )
            }
        })
    }

    private val coroutineContextFqName = StandardNames.COROUTINES_PACKAGE_FQ_NAME.child(Name.identifier("coroutineContext"))
    private val todoFqName = FqName("kotlin.TODO")

    context(KaSession)
    private fun KaCallableSymbol.isSuspendSymbol(): Boolean {
        // Currently, Kotlin does not support suspending properties except for accessing the coroutineContext
        if (this is KaKotlinPropertySymbol && getFqNameIfPackageOrNonLocal() == coroutineContextFqName) {
            return true
        }
        return this is KaNamedFunctionSymbol && isSuspend
    }

    context(KaSession)
    private fun KaCallableSymbol.isTodoSymbol(): Boolean =
        this is KaNamedFunctionSymbol && callableId?.asSingleFqName() == todoFqName

    context(KaSession)
    private fun KtNamedFunction.isSuspendModifierRedundant(): Boolean {
        var isSuspendModifierRedundant = true
        val containingFunction = this

        val expressions = containingFunction.locallyExecutedExpressions()

        KotlinCallProcessor.process(expressions, object : KotlinCallTargetProcessor {
            override fun KaSession.processCallTarget(target: CallTarget): Boolean {
                val symbol = target.symbol
                if ((symbol.isSuspendSymbol() && symbol.psi != containingFunction) || symbol.isTodoSymbol()) {
                    isSuspendModifierRedundant = false
                    return false
                }
                return true
            }

            override fun KaSession.processUnresolvedCall(element: KtElement, callInfo: KaCallInfo?): Boolean {
                if (callInfo != null) {
                    // A callInfo of null means that the element could not be resolved at all, so it is not even unresolved.
                    // For example, if the element is not an expression, so we ignore those cases.
                    isSuspendModifierRedundant = false
                    return false
                }
                return true
            }
        })

        if (isSuspendModifierRedundant) {
            ReferencesSearch
                .search(containingFunction, LocalSearchScope(containingFunction.parent))
                .forEach(Processor { reference ->
                    val expression =
                        reference.element.parent.findParentOfType<KtExpression>() ?: return@Processor isSuspendModifierRedundant
                    analyze(expression) {
                        val call = expression.resolveToCall() ?: return@analyze
                        val functionCall = call.singleFunctionCallOrNull() ?: return@analyze

                        val anySuspendFunctionType =
                            functionCall.argumentMapping.any {
                                (it.value.returnType as? KaFunctionType)?.isSuspend == true
                            }
                        if (anySuspendFunctionType) {
                            isSuspendModifierRedundant = false
                        }
                    }
                    isSuspendModifierRedundant
                })
        }

        return isSuspendModifierRedundant
    }

    /**
     * Produces a [Sequence] containing all the [KtExpression]s which are executed in the context of [this] function's body.
     *
     * For example, it includes the expressions from the lambdas if they happen to be inline,
     * but it does not return the expressions from the local classes' or local functions' bodies.
     */
    context(KaSession)
    private fun KtNamedFunction.locallyExecutedExpressions(): Sequence<KtExpression> {
        val functionBody = bodyExpression ?: return emptySequence()

        return functionBody
            .descendants { element ->
                when (element) {
                    is KtClassLikeDeclaration -> false
                    is KtFunction -> isInlinedArgument(element)
                    else -> true
                }
            }
            .filterIsInstance<KtExpression>()
    }
}