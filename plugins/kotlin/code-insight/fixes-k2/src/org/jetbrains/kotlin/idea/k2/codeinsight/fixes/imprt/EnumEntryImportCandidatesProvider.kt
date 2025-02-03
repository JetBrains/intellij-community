// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiEnumConstant
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtEnumEntry

internal class EnumEntryImportCandidatesProvider(positionContext: KotlinNameReferencePositionContext) :
    AbstractImportCandidatesProvider(positionContext) {

    private fun acceptsKotlinEnumEntry(enumEntry: KtEnumEntry): Boolean {
        return !enumEntry.isImported() && enumEntry.canBeImported()
    }

    private fun acceptsJavaEnumEntry(enumEntry: PsiEnumConstant): Boolean {
        return !enumEntry.isImported() && enumEntry.canBeImported()
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectCandidates(indexProvider: KtSymbolFromIndexProvider): List<CallableImportCandidate> {
        if (positionContext.explicitReceiver != null) return emptyList()

        val unresolvedName = positionContext.name

        val kotlinEnumEntries = indexProvider.getKotlinEnumEntriesByName(
            name = unresolvedName,
            psiFilter = { acceptsKotlinEnumEntry(it) },
        )

        val javaEnumEntries = indexProvider.getJavaFieldsByName(
            name = unresolvedName,
            psiFilter = { it is PsiEnumConstant && acceptsJavaEnumEntry(it) },
        ).filterIsInstance<KaEnumEntrySymbol>()
        
        val visibilityChecker = createUseSiteVisibilityChecker(getFileSymbol(), receiverExpression = null, positionContext.position)
        
        return (kotlinEnumEntries + javaEnumEntries)
            .map { CallableImportCandidate.create(it) }
            .filter { it.isVisible(visibilityChecker) }
            .toList()
    }
}