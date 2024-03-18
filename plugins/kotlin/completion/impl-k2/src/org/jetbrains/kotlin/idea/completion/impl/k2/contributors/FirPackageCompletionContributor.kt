// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext

internal class FirPackageCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<KotlinRawPositionContext>(basicContext, priority) {

    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val rootSymbol = if (positionContext !is KotlinNameReferencePositionContext || positionContext.explicitReceiver == null) {
            ROOT_PACKAGE_SYMBOL
        } else {
            positionContext.explicitReceiver?.reference()?.resolveToSymbols()?.filterIsInstance<KtPackageSymbol>()?.singleOrNull()
        } ?: return

        val symbolOrigin = CompletionSymbolOrigin.Scope(KtScopeKind.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX))

        rootSymbol.getPackageScope()
            .getPackageSymbols(scopeNameFilter)
            .filterNot { it.fqName.isExcludedFromAutoImport(project, originalKtFile) }
            .forEach { packageSymbol ->
                val element = lookupElementFactory.createPackagePartLookupElement(packageSymbol.fqName)
                Weighers.applyWeighsToLookupElement(weighingContext, element, KtSymbolWithOrigin(packageSymbol, symbolOrigin))
                sink.addElement(element)
            }
    }
}