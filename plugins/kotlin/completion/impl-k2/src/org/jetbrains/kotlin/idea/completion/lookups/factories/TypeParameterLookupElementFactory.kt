// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.idea.completion.lookups.UniqueLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.withClassifierSymbolInfo

internal object TypeParameterLookupElementFactory {

    context(KaSession)
    fun createLookup(symbol: KaTypeParameterSymbol): LookupElementBuilder {
        return LookupElementBuilder.create(UniqueLookupObject(), symbol.name.asString())
            .let { withClassifierSymbolInfo(symbol, it) }
    }
}