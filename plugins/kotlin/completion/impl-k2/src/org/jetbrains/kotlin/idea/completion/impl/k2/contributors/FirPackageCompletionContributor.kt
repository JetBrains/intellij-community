// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtOutsideTowerScopeKinds
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.resolveReceiverToSymbols
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext

internal class FirPackageCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinRawPositionContext>(parameters, sink, priority) {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun complete(
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
    ) {
        val rootSymbol = positionContext.resolveReceiverToSymbols()
            .filterIsInstance<KaPackageSymbol>()
            .singleOrNull()
            ?: return

        rootSymbol.packageScope
            .getPackageSymbols(scopeNameFilter)
            .filterNot { it.fqName.isExcludedFromAutoImport(project, originalKtFile) }
            .map { packageSymbol ->
                KotlinFirLookupElementFactory.createPackagePartLookupElement(packageSymbol.fqName)
                    .applyWeighs(
                        context = weighingContext,
                        symbolWithOrigin = KtSymbolWithOrigin(
                            _symbol = packageSymbol,
                            scopeKind = KtOutsideTowerScopeKinds.PackageMemberScope,
                        ),
                    )
            }.forEach(sink::addElement)
    }
}