// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRanges
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddArgumentNamesApplicators
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument

internal class AddNamesToCallArgumentsIntention :
    AbstractKotlinApplicatorBasedIntention<KtCallElement, AddArgumentNamesApplicators.MultipleArgumentsInput>(KtCallElement::class) {
    override fun getApplicator() =
        AddArgumentNamesApplicators.multipleArgumentsApplicator

    override fun getApplicabilityRange() = applicabilityRanges { element: KtCallElement ->
        // Note: Applicability range matches FE 1.0 (see AddNamesToCallArgumentsIntention).
        val calleeExpression = element.calleeExpression ?: return@applicabilityRanges emptyList()
        val calleeExpressionTextRange = calleeExpression.textRangeIn(element)
        val arguments = element.valueArguments
        if (arguments.size < 2) {
            listOf(calleeExpressionTextRange)
        } else {
            val firstArgument = arguments.firstOrNull() as? KtValueArgument ?: return@applicabilityRanges emptyList()
            val endOffset = firstArgument.textRangeIn(element).endOffset
            listOf(TextRange(calleeExpressionTextRange.startOffset, endOffset))
        }
    }

    override fun getInputProvider() = inputProvider { element: KtCallElement ->
        val resolvedCall = element.resolveCall().singleFunctionCallOrNull() ?: return@inputProvider null

        if (!resolvedCall.symbol.hasStableParameterNames) {
            return@inputProvider null
        }

        val arguments = element.valueArgumentList?.arguments ?: return@inputProvider null
        AddArgumentNamesApplicators.MultipleArgumentsInput(arguments.associateWith {
            getArgumentNameIfCanBeUsedForCalls(it, resolvedCall) ?: return@inputProvider null
        })
    }

}