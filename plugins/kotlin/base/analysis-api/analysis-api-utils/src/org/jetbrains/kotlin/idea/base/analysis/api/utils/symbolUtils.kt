// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin

fun KaSymbolOrigin.isJavaSourceOrLibrary(): Boolean = this == KaSymbolOrigin.JAVA_SOURCE || this == KaSymbolOrigin.JAVA_LIBRARY

/**
 * Returns the same result as [KaSession.allOverriddenSymbols],
 * but includes the original symbol ([this]) at the beginning of the
 * resulting [Sequence].
 */
context(KaSession)
val KaCallableSymbol.allOverriddenSymbolsWithSelf: Sequence<KaCallableSymbol>
    get() {
        val originalSymbol = this

        return sequence {
            yield(originalSymbol)
            yieldAll(originalSymbol.allOverriddenSymbols)
        }
    }