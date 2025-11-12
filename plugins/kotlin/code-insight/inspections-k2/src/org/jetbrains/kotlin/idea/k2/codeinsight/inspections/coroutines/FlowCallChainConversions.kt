// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.coroutines

import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutinesIds
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.CallChainConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConversionId
import org.jetbrains.kotlin.name.CallableId

internal object FlowCallChainConversions {
    private val conversionsList: List<CallChainConversion> by lazy {
        listOf(
            CallChainConversion(CoroutinesIds.Flows.filter, CoroutinesIds.Flows.first, CoroutinesIds.Flows.first),
            CallChainConversion(CoroutinesIds.Flows.filter, CoroutinesIds.Flows.firstOrNull, CoroutinesIds.Flows.firstOrNull),
            CallChainConversion(CoroutinesIds.Flows.filter, CoroutinesIds.Flows.count, CoroutinesIds.Flows.count),

            CallChainConversion(CoroutinesIds.Flows.map, CoroutinesIds.Flows.filterNotNull, CoroutinesIds.Flows.mapNotNull),
        )
    }

    val conversionGroups: Map<ConversionId, List<CallChainConversion>> by lazy {
        conversionsList.groupBy { conversion -> conversion.id }
    }
}

private fun CallChainConversion(
    firstCallable: CallableId,
    secondCallable: CallableId,
    replacementCallable: CallableId
): CallChainConversion = CallChainConversion(
    firstCallable.asSingleFqName(),
    secondCallable.asSingleFqName(),
    replacementCallable.asSingleFqName()
)
