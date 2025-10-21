// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.fir

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.packageScope
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtOutsideTowerScopeKinds
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.resolveReceiverToSymbols
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributorBase
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2PackageCompletionContributor.Companion.shouldCompleteTopLevelPackages
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinImportDirectivePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPackageDirectivePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

internal class FirPackageCompletionContributor(
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinRawPositionContext>(sink, priority) {

    private fun KotlinRawPositionContext.isAppropriateContext(): Boolean {
        if (shouldCompleteTopLevelPackages()) return true
        return when (this) {
            is KotlinPackageDirectivePositionContext,
            is KotlinImportDirectivePositionContext,
            is KotlinTypeNameReferencePositionContext -> true
            else -> position.parent?.parent is KtDotQualifiedExpression
        }
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun complete(
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
    ) {
        // Allow disabling top-level package completion for LSP: KTIJ-35650
        if (!positionContext.isAppropriateContext()) {
            return
        }
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