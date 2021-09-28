// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.analysis.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.canBeReplacedWithInvokeCall
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

object ReplaceWithSafeCallFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val psiElement = diagnostic.psiElement
        val qualifiedExpression = psiElement.parent as? KtDotQualifiedExpression
        if (qualifiedExpression != null) {
            val call = qualifiedExpression.callExpression
            if (call != null) {
                val context = qualifiedExpression.analyze(BodyResolveMode.PARTIAL)
                val ktPsiFactory = KtPsiFactory(psiElement)
                val safeQualifiedExpression = ktPsiFactory.createExpressionByPattern(
                    "$0?.$1", qualifiedExpression.receiverExpression, call,
                    reformat = false
                )
                val newContext = safeQualifiedExpression.analyzeAsReplacement(qualifiedExpression, context)
                if (safeQualifiedExpression.getResolvedCall(newContext)?.canBeReplacedWithInvokeCall() == true) {
                    return ReplaceInfixOrOperatorCallFix(call, call.shouldHaveNotNullType())
                }
            }
            return ReplaceWithSafeCallFix(qualifiedExpression, qualifiedExpression.shouldHaveNotNullType())
        } else {
            if (psiElement !is KtNameReferenceExpression) return null
            if (psiElement.getResolvedCall(psiElement.analyze())?.getImplicitReceiverValue() != null) {
                val expressionToReplace: KtExpression = psiElement.parent as? KtCallExpression ?: psiElement
                return ReplaceImplicitReceiverCallFix(expressionToReplace, expressionToReplace.shouldHaveNotNullType())
            }
            return null
        }
    }
}

object ReplaceWithSafeCallForScopeFunctionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = diagnostic.psiElement
        val scopeFunctionLiteral = element.getStrictParentOfType<KtFunctionLiteral>() ?: return null
        val scopeCallExpression = scopeFunctionLiteral.getStrictParentOfType<KtCallExpression>() ?: return null
        val scopeDotQualifiedExpression = scopeCallExpression.getStrictParentOfType<KtDotQualifiedExpression>() ?: return null

        val context = scopeCallExpression.analyze()
        val scopeFunctionLiteralDescriptor = context[BindingContext.FUNCTION, scopeFunctionLiteral] ?: return null
        val scopeFunctionKind = scopeCallExpression.scopeFunctionKind(context) ?: return null

        val internalReceiver = (element.parent as? KtDotQualifiedExpression)?.receiverExpression
        val internalReceiverDescriptor = internalReceiver.getResolvedCall(context)?.candidateDescriptor
        val internalResolvedCall = (element.getParentOfType<KtElement>(strict = false))?.getResolvedCall(context)
            ?: return null

        when (scopeFunctionKind) {
            ScopeFunctionKind.WITH_PARAMETER -> {
                if (internalReceiverDescriptor != scopeFunctionLiteralDescriptor.valueParameters.singleOrNull()) {
                    return null
                }
            }
            ScopeFunctionKind.WITH_RECEIVER -> {
                if (internalReceiverDescriptor != scopeFunctionLiteralDescriptor.extensionReceiverParameter &&
                    internalResolvedCall.getImplicitReceiverValue() == null
                ) {
                    return null
                }
            }
        }

        return ReplaceWithSafeCallForScopeFunctionFix(
            scopeDotQualifiedExpression, scopeDotQualifiedExpression.shouldHaveNotNullType()
        )
    }

    private fun KtCallExpression.scopeFunctionKind(context: BindingContext): ScopeFunctionKind? {
        val methodName = getResolvedCall(context)?.resultingDescriptor?.fqNameUnsafe?.asString()
        return ScopeFunctionKind.values().firstOrNull { kind -> kind.names.contains(methodName) }
    }

    private enum class ScopeFunctionKind(vararg val names: String) {
        WITH_PARAMETER("kotlin.let", "kotlin.also"),
        WITH_RECEIVER("kotlin.apply", "kotlin.run")
    }
}

