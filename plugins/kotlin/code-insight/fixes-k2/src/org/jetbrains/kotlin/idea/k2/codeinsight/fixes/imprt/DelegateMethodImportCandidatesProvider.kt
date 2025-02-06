// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPropertyDelegatePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class DelegateMethodImportCandidatesProvider(
    private val expectedDelegateFunctionSignature: String,
    override val positionContext: KotlinPropertyDelegatePositionContext,
) : AbstractImportCandidatesProvider() {

    private val expectedDelegateFunctionName: Name? = listOf(
        OperatorNameConventions.GET_VALUE,
        OperatorNameConventions.SET_VALUE,
    ).singleOrNull { expectedDelegateFunctionSignature.startsWith(it.asString() + "(") }

    private val missingDelegateFunctionNames: List<Name> =
        listOfNotNull(
            expectedDelegateFunctionName,
            OperatorNameConventions.PROVIDE_DELEGATE,
        )

    private fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean {
        if (!kotlinCallable.hasModifier(KtTokens.OPERATOR_KEYWORD)) return false

        return !kotlinCallable.isImported() && kotlinCallable.canBeImported()
    }

    context(KaSession)
    override fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<CallableImportCandidate> {
        val expressionType = positionContext.propertyDelegate.expression?.expressionType ?: return emptyList()
        return indexProvider.getExtensionCallableSymbolsByNameFilter(
            nameFilter = { it in missingDelegateFunctionNames },
            receiverTypes = listOf(expressionType),
        ) { acceptsKotlinCallable(it) }
            .map { CallableImportCandidate.create(it) }
            .toList()
    }
}
