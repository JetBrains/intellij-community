// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKinds
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.resolveToSymbols
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext

internal class FirPackageCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinRawPositionContext>(basicContext, priority) {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun complete(
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {

        val rootSymbol = positionContext.resolveToSymbols()
            .filterIsInstance<KaPackageSymbol>()
            .singleOrNull()
            ?: return

        val symbolOrigin = CompletionSymbolOrigin.Scope(KaScopeKinds.PackageMemberScope(CompletionSymbolOrigin.SCOPE_OUTSIDE_TOWER_INDEX))

        rootSymbol.packageScope
            .getPackageSymbols(scopeNameFilter)
            .filterNot { it.fqName.isExcludedFromAutoImport(project, originalKtFile) }
            .forEach { packageSymbol ->
                val element = KotlinFirLookupElementFactory.createPackagePartLookupElement(packageSymbol.fqName)
                Weighers.applyWeighsToLookupElement(weighingContext, element, KtSymbolWithOrigin(packageSymbol, symbolOrigin))
                sink.addElement(element)
            }
    }
}