// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

internal object ChangeToLabeledReturnFixFactory: KotlinIntentionActionsFactory() {
    private fun findAccessibleLabels(bindingContext: BindingContext, position: KtReturnExpression): List<Name> {
        val result = mutableListOf<Name>()
        for (parent in position.parentsWithSelf) {
            when (parent) {
                is KtClassOrObject -> break
                is KtFunctionLiteral -> {
                    val (label, call) = parent.findLabelAndCall()
                    if (label != null) {
                        result.add(label)
                    }

                    // check if the current function literal is inlined and stop processing outer declarations if it's not
                    val callee = call?.calleeExpression as? KtReferenceExpression ?: break
                    if (!InlineUtil.isInline(bindingContext[BindingContext.REFERENCE_TARGET, callee])) break
                }
                else -> {}
            }
        }
        return result
    }

    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val element = diagnostic.psiElement as? KtElement ?: return emptyList()
        val context by lazy { element.analyze() }

        val returnExpression = when (diagnostic.factory) {
            Errors.RETURN_NOT_ALLOWED ->
                diagnostic.psiElement as? KtReturnExpression
            Errors.TYPE_MISMATCH,
            Errors.TYPE_MISMATCH_WARNING,
            Errors.CONSTANT_EXPECTED_TYPE_MISMATCH,
            Errors.NULL_FOR_NONNULL_TYPE ->
                getLambdaReturnExpression(diagnostic.psiElement, context)
            else -> null
        } ?: return emptyList()

        val candidates = findAccessibleLabels(context, returnExpression)
        return candidates
            .map { ChangeToLabeledReturnFix(returnExpression, labeledReturn = "return@${it.render()}") }
            .map(ModCommandAction::asIntention)
    }

    private fun getLambdaReturnExpression(element: PsiElement, bindingContext: BindingContext): KtReturnExpression? {
        val returnExpression = element.getStrictParentOfType<KtReturnExpression>() ?: return null
        val lambda = returnExpression.getStrictParentOfType<KtLambdaExpression>() ?: return null
        val lambdaReturnType = bindingContext[BindingContext.FUNCTION, lambda.functionLiteral]?.returnType ?: return null
        val returnType = returnExpression.returnedExpression?.getType(bindingContext) ?: return null
        if (!returnType.isSubtypeOf(lambdaReturnType)) return null
        return returnExpression
    }
}