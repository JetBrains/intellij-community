// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector

@ApiStatus.Internal
data class CallableInsertionOptions(
    val importingStrategy: ImportStrategy,
    val insertionStrategy: CallableInsertionStrategy,
) {
    fun withImportingStrategy(newImportStrategy: ImportStrategy): CallableInsertionOptions =
        copy(importingStrategy = newImportStrategy)

    fun withInsertionStrategy(newInsertionStrategy: CallableInsertionStrategy): CallableInsertionOptions =
        copy(insertionStrategy = newInsertionStrategy)
}

internal fun KtAnalysisSession.detectCallableOptions(symbol: KtCallableSymbol, importStrategyDetector: ImportStrategyDetector): CallableInsertionOptions {
    return CallableInsertionOptions(
        importingStrategy = importStrategyDetector.detectImportStrategy(symbol),
        insertionStrategy = when (symbol) {
            is KtFunctionSymbol -> CallableInsertionStrategy.AsCall
            else -> CallableInsertionStrategy.AsIdentifier
        }
    )
}
