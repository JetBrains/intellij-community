// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.name.Name

/**
 * Consider moving this to [KtSymbolFromIndexProvider] when there is a good API to represent inherited callables.
 */
context(KaSession)
internal fun KtSymbolFromIndexProvider.getCallableSymbolsFromSubclassObjects(name: Name): Sequence<Pair<KaClassSymbol, KaCallableSymbol>> {
    val allObjects = getKotlinSubclassObjectsByNameFilter(nameFilter = { true })

    return allObjects.flatMap { objectSymbol ->
        val callablesByName = objectSymbol.memberScope.callables(name)
        callablesByName.map { objectSymbol to it }
    }
}

/**
 * Consider moving this to [KtSymbolFromIndexProvider] when there is a good API to represent inherited callables.
 */
context(KaSession)
internal fun KtSymbolFromIndexProvider.getExtensionCallableSymbolsFromSubclassObjects(
    name: Name,
    receiverTypes: List<KaType>,
): Sequence<Pair<KaClassSymbol, KaCallableSymbol>> =
    getCallableSymbolsFromSubclassObjects(name).filterExtensionsByReceiverTypes(receiverTypes)

/**
 * Mostly a copy of [KtSymbolFromIndexProvider.filterExtensionsByReceiverTypes]; should be unified in the future.
 */
context(KaSession)
private fun Sequence<Pair<KaClassSymbol, KaCallableSymbol>>.filterExtensionsByReceiverTypes(
    receiverTypes: List<KaType>
): Sequence<Pair<KaClassSymbol, KaCallableSymbol>> {
    val nonNullableReceiverTypes = receiverTypes.map { it.withNullability(KaTypeNullability.NON_NULLABLE) }

    return filter { (_, symbol) ->
        if (!symbol.isExtension) return@filter false
        val symbolReceiverType = symbol.receiverType ?: return@filter false

        nonNullableReceiverTypes.any { it isPossiblySubTypeOf symbolReceiverType }
    }
}
