// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

/**
 * Prefer Duration-based overloads (e.g., delay(Duration)) over Long-based ones (e.g., delay(Long)).
 */
internal object DurationPreferringWeigher: KotlinLookupElementWeigher("kotlin.durationPreferring"), KotlinSectionContextWeigher {

    private const val DURATION_TYPE_FQ_NAME = "kotlin.time.Duration"
    private const val LONG_TYPE_FQ_NAME = "kotlin.Long"

    // The default rank is neutral (1). Duration overloads get rank 0 so they sort before Long (1).
    private var LookupElement.durationRank: Int
            by NotNullableUserDataProperty(Key("KOTLIN_DURATION_PREFER_RANK"), 1)

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    override fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>?) {
        val function = symbolWithOrigin?.symbol as? KaFunctionSymbol ?: return
        val callableId = function.callableId ?: return

        // Check if this function is one of the supported Duration-accepting functions
        if (callableId !in supportedDurationFunctions) return

        val firstParamType = function.valueParameters.firstOrNull()?.returnType as? KaClassType ?: return
        val fqName = firstParamType.classId.asSingleFqName().asString()

        lookupElement.durationRank = when (fqName) {
            DURATION_TYPE_FQ_NAME -> 0
            LONG_TYPE_FQ_NAME -> 1
            else -> 1
        }
    }

    override fun weigh(element: LookupElement): Int = element.durationRank

    private val supportedDurationFunctions = setOf(
        // kotlinx.coroutines functions
        CallableId(FqName("kotlinx.coroutines"), Name.identifier("delay")),
        CallableId(FqName("kotlinx.coroutines"), Name.identifier("withTimeout")),
        CallableId(FqName("kotlinx.coroutines"), Name.identifier("withTimeoutOrNull")),
        
        // kotlinx.coroutines.flow functions
        CallableId(FqName("kotlinx.coroutines.flow"), Name.identifier("debounce")),
        CallableId(FqName("kotlinx.coroutines.flow"), Name.identifier("sample")),
        CallableId(FqName("kotlinx.coroutines.flow"), Name.identifier("throttle")),
        CallableId(FqName("kotlinx.coroutines.flow"), Name.identifier("timeout")),
        CallableId(FqName("kotlinx.coroutines.flow"), Name.identifier("retryWhen")),
        
        // kotlinx.coroutines.selects functions
        CallableId(FqName("kotlinx.coroutines.selects"), Name.identifier("onTimeout")),
        
        // kotlinx.coroutines.test functions
        CallableId(FqName("kotlinx.coroutines.test"), Name.identifier("advanceTimeBy")),
    )
}