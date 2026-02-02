// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.packageScope
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
import org.jetbrains.kotlin.idea.util.positionContext.KDocLinkNamePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinAnnotationTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinImportDirectivePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPackageDirectivePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinWithSubjectEntryPositionContext
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

internal class K2PackageCompletionContributor : K2SimpleCompletionContributor<KotlinRawPositionContext>(
    KotlinRawPositionContext::class
) {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    override fun shouldExecute(): Boolean {
        return !context.positionContext.isAfterRangeOperator() && !context.positionContext.allowsOnlyNamedArguments()
    }

    companion object {
        internal fun shouldCompleteTopLevelPackages(): Boolean =
            Registry.`is`("kotlin.k2.complete.top.level.packages", true)
    }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    override fun complete() {
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
                        symbolWithOrigin = KtSymbolWithOrigin(
                            _symbol = packageSymbol,
                            scopeKind = KtOutsideTowerScopeKinds.PackageMemberScope,
                        ),
                    )
            }.forEach { addElement(it) }
    }

    override fun K2CompletionSetupScope<KotlinRawPositionContext>.isAppropriatePosition(): Boolean = when (position) {
        is KotlinTypeNameReferencePositionContext -> {
            position.allowsClassifiersAndPackagesForPossibleExtensionCallables(
                parameters = completionContext.parameters,
                prefixMatcher = completionContext.prefixMatcher,
            )
        }

        is KotlinPackageDirectivePositionContext,
        is KotlinImportDirectivePositionContext -> true

        is KotlinWithSubjectEntryPositionContext,
        is KotlinAnnotationTypeNameReferencePositionContext,
        is KotlinExpressionNameReferencePositionContext,
        is KDocLinkNamePositionContext -> {
            // Allow disabling top-level package completion for LSP: KTIJ-35650
            shouldCompleteTopLevelPackages() || position.position.parent?.parent is KtDotQualifiedExpression
        }

        else -> false
    }

    override fun K2CompletionSectionContext<KotlinRawPositionContext>.getGroupPriority(): Int = when (positionContext) {
        is KotlinWithSubjectEntryPositionContext -> 3
        is KotlinTypeNameReferencePositionContext, is KotlinAnnotationTypeNameReferencePositionContext -> 2
        is KotlinExpressionNameReferencePositionContext, is KDocLinkNamePositionContext -> 1
        else -> 0
    }
}