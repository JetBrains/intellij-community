// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtOutsideTowerScopeKinds
import org.jetbrains.kotlin.idea.completion.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.contributors.helpers.resolveReceiverToSymbols
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.allowsOnlyNamedArguments
import org.jetbrains.kotlin.idea.completion.impl.k2.isAfterRangeOperator
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.util.positionContext.*

internal class K2PackageCompletionContributor : K2SimpleCompletionContributor<KotlinRawPositionContext>(
    KotlinRawPositionContext::class
) {
    override fun KaSession.shouldExecute(context: K2CompletionSectionContext<KotlinRawPositionContext>): Boolean {
        return !context.positionContext.isAfterRangeOperator() && !context.positionContext.allowsOnlyNamedArguments()
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.complete(context: K2CompletionSectionContext<KotlinRawPositionContext>) {
        val rootSymbol = context.positionContext.resolveReceiverToSymbols()
            .filterIsInstance<KaPackageSymbol>()
            .singleOrNull()
            ?: return

        rootSymbol.packageScope
            .getPackageSymbols(context.completionContext.scopeNameFilter)
            .filterNot { it.fqName.isExcludedFromAutoImport(context.project, context.parameters.originalFile) }
            .map { packageSymbol ->
                KotlinFirLookupElementFactory.createPackagePartLookupElement(packageSymbol.fqName)
                    .applyWeighs(
                        context = context.weighingContext,
                        symbolWithOrigin = KtSymbolWithOrigin(
                            _symbol = packageSymbol,
                            scopeKind = KtOutsideTowerScopeKinds.PackageMemberScope,
                        ),
                    )
            }.forEach { context.addElement(it) }
    }

    override fun K2CompletionSetupScope<KotlinRawPositionContext>.isAppropriatePosition(): Boolean = when (position) {
        is KotlinTypeNameReferencePositionContext -> {
            position.allowsClassifiersAndPackagesForPossibleExtensionCallables(
                parameters = completionContext.parameters,
                prefixMatcher = completionContext.prefixMatcher,
            )
        }

        is KotlinWithSubjectEntryPositionContext,
        is KotlinAnnotationTypeNameReferencePositionContext,
        is KotlinExpressionNameReferencePositionContext,
        is KotlinImportDirectivePositionContext,
        is KotlinPackageDirectivePositionContext,
        is KDocLinkNamePositionContext -> true

        else -> false
    }

    override fun K2CompletionSectionContext<KotlinRawPositionContext>.getGroupPriority(): Int = when (positionContext) {
        is KotlinWithSubjectEntryPositionContext -> 3
        is KotlinTypeNameReferencePositionContext, is KotlinAnnotationTypeNameReferencePositionContext -> 2
        is KotlinExpressionNameReferencePositionContext, is KDocLinkNamePositionContext -> 1
        else -> 0
    }
}