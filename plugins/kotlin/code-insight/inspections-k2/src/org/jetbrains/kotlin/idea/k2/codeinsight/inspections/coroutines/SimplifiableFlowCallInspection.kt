// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.coroutines

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutinesIds
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.AbstractSimplifiableCallInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.isIdentityLambda
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument

internal class SimplifiableFlowCallInspection : AbstractSimplifiableCallInspection() {
    private class FlowFlatMapToFlattenConversion(
        targetFqName: CallableId,
        replacementFqName: CallableId,
    ) : Conversion(
        targetFqName.asSingleFqName(),
        replacementFqName.asSingleFqName()
    ) {
        context(_: KaSession)
        override fun analyze(callExpression: KtCallExpression): String? {
            val functionCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null

            val transformArgument = functionCall.findArgumentExpressionByParameterName(CoroutinesIds.ParameterNames.transform) as? KtLambdaExpression ?: return null
            if (!transformArgument.isIdentityLambda()) return null

            val concurrencyValueArgument = functionCall.findArgumentExpressionByParameterName(CoroutinesIds.ParameterNames.concurrency)?.parent as? KtValueArgument

            return "${replacementFqName.shortName()}(${concurrencyValueArgument?.text.orEmpty()})"
        }
    }

    override val conversions: List<Conversion>
        get() = listOf(
            FlowFlatMapToFlattenConversion(
                targetFqName = CoroutinesIds.Flows.flatMapMerge,
                replacementFqName = CoroutinesIds.Flows.flattenMerge,
            ),
            FlowFlatMapToFlattenConversion(
                targetFqName = CoroutinesIds.Flows.flatMapConcat,
                replacementFqName = CoroutinesIds.Flows.flattenConcat,
            ),
            FilterToFilterNotNullConversion(
                targetFqName = CoroutinesIds.Flows.filter.asSingleFqName(),
                replacementFqName = CoroutinesIds.Flows.filterNotNull.asSingleFqName(),
            ),
            FilterToFilterIsInstanceConversion(
                targetFqName = CoroutinesIds.Flows.filter.asSingleFqName(),
                replacementFqName = CoroutinesIds.Flows.filterIsInstance.asSingleFqName(),
            ),
        )

}

private fun KaFunctionCall<*>.findArgumentExpressionByParameterName(parameterName: Name): KtExpression? {
    val matchingEntry = argumentMapping.entries.find { (_, parameter) -> parameter.name == parameterName }
    return matchingEntry?.key
}
