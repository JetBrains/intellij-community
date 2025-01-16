// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol

internal sealed interface ImportCandidate {
    val symbol: KaDeclarationSymbol
}

internal data class ClassLikeImportCandidate(override val symbol: KaClassLikeSymbol): ImportCandidate

internal data class CallableImportCandidate(override val symbol: KaCallableSymbol): ImportCandidate