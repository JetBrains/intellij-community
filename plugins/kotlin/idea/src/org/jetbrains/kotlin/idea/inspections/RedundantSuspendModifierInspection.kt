// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.highlighter.SuspendCallKind
import org.jetbrains.kotlin.idea.highlighter.getSuspendCallKind
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.namedFunctionVisitor
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class RedundantSuspendModifierInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return namedFunctionVisitor(fun(function) {
            if (!function.languageVersionSettings.supportsFeature(LanguageFeature.Coroutines)) return

            val suspendModifier = function.modifierList?.getModifier(KtTokens.SUSPEND_KEYWORD) ?: return
            if (!function.hasBody()) return
            if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD) || function.hasModifier(KtTokens.ACTUAL_KEYWORD)) return

            val context = function.analyzeWithContent()
            val descriptor = context[BindingContext.FUNCTION, function] ?: return
            if (descriptor.modality == Modality.OPEN) return

            if (function.hasSuspendCalls(context)) return

            if (function.hasAnyUnresolvedCalls(context)) return

            holder.registerProblem(
                suspendModifier,
                KotlinBundle.message("redundant.suspend.modifier"),
                IntentionWrapper(RemoveModifierFixBase(function, KtTokens.SUSPEND_KEYWORD, isRedundant = true).asIntention())
            )
        })
    }

    private fun KtNamedFunction.hasAnyUnresolvedCalls(context: BindingContext): Boolean {
        return context.diagnostics.any {
            it.factory == Errors.UNRESOLVED_REFERENCE && this.isAncestor(it.psiElement)
        }
    }

    private fun KtNamedFunction.hasSuspendCalls(bindingContext: BindingContext): Boolean {
        val selfDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, this] ?: return false

        return anyDescendantOfType<KtExpression> { expression ->
            val kind = getSuspendCallKind(expression, bindingContext) ?: return@anyDescendantOfType false
            if (kind is SuspendCallKind.FunctionCall) {
                val resolvedCall = kind.element.getResolvedCall(bindingContext)
                if (resolvedCall != null) {
                    val isSelfCall = when (resolvedCall) {
                        is VariableAsFunctionResolvedCall -> selfDescriptor == resolvedCall.functionCall.candidateDescriptor.original
                        else -> selfDescriptor == resolvedCall.candidateDescriptor.original
                    }

                    if (isSelfCall) {
                        return@anyDescendantOfType false
                    }
                }
            }

            return@anyDescendantOfType true
        }
    }
}
