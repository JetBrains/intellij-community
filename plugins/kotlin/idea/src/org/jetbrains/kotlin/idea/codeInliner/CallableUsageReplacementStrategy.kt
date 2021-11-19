// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInliner

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.intentions.OperatorToFunctionIntention
import org.jetbrains.kotlin.idea.intentions.isInvokeOperator
import org.jetbrains.kotlin.idea.util.application.withPsiAttachment
import org.jetbrains.kotlin.idea.resolve.getLanguageVersionSettings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

private val LOG = Logger.getInstance(CallableUsageReplacementStrategy::class.java)

class CallableUsageReplacementStrategy(
    private val replacement: CodeToInline,
    private val inlineSetter: Boolean = false
) : UsageReplacementStrategy {
    override fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)? {
        val bindingContext = usage.analyze(BodyResolveMode.PARTIAL_WITH_CFA)
        val resolvedCall = usage.getResolvedCall(bindingContext) ?: return null
        if (!resolvedCall.status.isSuccess) return null

        val callElement = when (resolvedCall) {
            is VariableAsFunctionResolvedCall -> {
                val callElement = resolvedCall.variableCall.call.callElement
                if (resolvedCall.resultingDescriptor.isInvokeOperator &&
                    replacement.mainExpression?.getPossiblyQualifiedCallExpression() != null
                ) {
                    callElement.parent as? KtCallExpression ?: callElement
                } else {
                    callElement
                }
            }
            else -> resolvedCall.call.callElement
        }

        if (!CodeInliner.canBeReplaced(callElement)) return null

        val languageVersionSettings = usage.getResolutionFacade().getLanguageVersionSettings()
        //TODO: precheck pattern correctness for annotation entry

        return when {
            usage is KtArrayAccessExpression || usage is KtCallExpression -> {
                {
                    val nameExpression = OperatorToFunctionIntention.convert(usage).second
                    createReplacer(nameExpression)?.invoke()
                }
            }
            usage is KtOperationReferenceExpression && usage.getReferencedNameElementType() != KtTokens.IDENTIFIER -> {
                {
                    val nameExpression = OperatorToFunctionIntention.convert(usage.parent as KtExpression).second
                    createReplacer(nameExpression)?.invoke()
                }
            }
            usage is KtSimpleNameExpression -> {
                {
                    CodeInliner(languageVersionSettings, usage, bindingContext, resolvedCall, callElement, inlineSetter, replacement).doInline()
                }
            }
            else -> {
                val exceptionWithAttachments = KotlinExceptionWithAttachments("Unsupported usage")
                    .withAttachment("type", usage)
                    .withPsiAttachment("usage.kt", usage)

                LOG.error(exceptionWithAttachments)
                null
            }
        }
    }
}

