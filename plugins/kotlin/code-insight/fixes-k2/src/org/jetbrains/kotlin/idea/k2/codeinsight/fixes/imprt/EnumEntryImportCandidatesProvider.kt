// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtEnumEntry

internal class EnumEntryImportCandidatesProvider(positionContext: KotlinNameReferencePositionContext) :
    AbstractImportCandidatesProvider(positionContext) {

    private fun acceptsEnumEntry(enumEntry: KtEnumEntry): Boolean {
        return !enumEntry.isImported() && enumEntry.canBeImported()
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectCandidates(indexProvider: KtSymbolFromIndexProvider): List<CallableImportCandidate> {
        if (positionContext.explicitReceiver != null) return emptyList()

        val unresolvedName = positionContext.name

        val kotlinEnumEntries = indexProvider.getKotlinEnumEntriesByName(
            name = unresolvedName,
            psiFilter = { acceptsEnumEntry(it) },
        )
        
        val visibilityChecker = createUseSiteVisibilityChecker(getFileSymbol(), receiverExpression = null, positionContext.position)
        
        return kotlinEnumEntries
            .map { CallableImportCandidate.create(it) }
            .filter { it.isVisible(visibilityChecker) }
            .toList()
    }
}