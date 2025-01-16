// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider

internal interface ImportCandidatesProvider {
    context(KaSession)
    fun collectCandidateSymbols(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<KaDeclarationSymbol>

    context(KaSession)
    fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate> = collectCandidateSymbols(indexProvider)
        .map { importCandidate ->
            when (importCandidate) {
                is KaCallableSymbol -> CallableImportCandidate(importCandidate)
                is KaClassLikeSymbol -> ClassLikeImportCandidate(importCandidate)

                else -> error("Unexpected importCandidate: $importCandidate")
            }
        }
}