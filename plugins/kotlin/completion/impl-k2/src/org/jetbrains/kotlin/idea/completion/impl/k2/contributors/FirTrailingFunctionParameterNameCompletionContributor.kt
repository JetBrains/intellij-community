// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.implCommon.stringTemplates.createLookupElements
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.isFirstStatement

internal class FirTrailingFunctionParameterNameCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinExpressionNameReferencePositionContext>(parameters, sink, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KotlinExpressionNameReferencePositionContext,
        weighingContext: WeighingContext
    ) {
        if (positionContext.explicitReceiver != null) return

        val callExpression = positionContext.nameExpression
            .takeIf { it.isFirstStatement() }
            ?.parentOfType<KtCallExpression>()
            ?: return

        if (callExpression.lambdaArguments
                .firstOrNull()
                ?.getLambdaExpression()
                ?.functionLiteral
                ?.arrow != null
        ) return

        val (trailingFunctionType, suggestedParameterNames) = callExpression.resolveToCall()
            ?.singleFunctionCallOrNull()
            ?.partiallyAppliedSymbol
            ?.signature
            ?.let { FunctionLookupElementFactory.getTrailingFunctionSignature(it) }
            ?.let { FunctionLookupElementFactory.createTrailingFunctionDescriptor(it) }
            ?: return

        createLookupElements(trailingFunctionType, suggestedParameterNames)
            .forEach(sink::addElement)
    }
}
