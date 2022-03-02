// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.idea.completion.lookups.UniqueLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.withSymbolInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol

internal class TypeParameterLookupElementFactory {
    fun KtAnalysisSession.createLookup(symbol: KtTypeParameterSymbol): LookupElementBuilder {
        return LookupElementBuilder.create(UniqueLookupObject(), symbol.name.asString())
            .let { withSymbolInfo(symbol, it) }
    }
}