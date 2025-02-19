// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPropertyDelegatePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration

// TODO Get rid of this provider, use CallableImportCandidatesProvider instead
internal class DelegateMethodImportCandidatesProvider(
    override val positionContext: KotlinPropertyDelegatePositionContext,
) : AbstractImportCandidatesProvider() {

    private fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean {
        if (!kotlinCallable.hasModifier(KtTokens.OPERATOR_KEYWORD)) return false

        return !kotlinCallable.isImported() && kotlinCallable.canBeImported()
    }

    context(KaSession)
    override fun collectCandidates(
        name: Name,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<CallableImportCandidate> {
        val expressionType = positionContext.propertyDelegate.expression?.expressionType ?: return emptyList()
        return indexProvider.getExtensionCallableSymbolsByName(
            name = name,
            receiverTypes = listOf(expressionType),
        ) { acceptsKotlinCallable(it) }
            .map { CallableImportCandidate.create(it) }
            .toList()
    }
}
