// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector

@ApiStatus.Internal
@Serializable
data class CallableInsertionOptions(
    val importingStrategy: ImportStrategy,
    val insertionStrategy: CallableInsertionStrategy,
)

context(KaSession)
internal fun detectCallableOptions(symbol: KaCallableSymbol, importStrategyDetector: ImportStrategyDetector): CallableInsertionOptions {
    return CallableInsertionOptions(
        importingStrategy = importStrategyDetector.detectImportStrategyForCallableSymbol(symbol),
        insertionStrategy = when (symbol) {
            is KaNamedFunctionSymbol -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }
    )
}
