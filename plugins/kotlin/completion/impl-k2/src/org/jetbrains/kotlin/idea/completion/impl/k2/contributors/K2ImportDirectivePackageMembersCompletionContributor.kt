// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.asSignature
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.resolveReceiverToSymbols
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.staticScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.CallableInsertionOptions
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.CallableInsertionStrategy
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.impl.k2.weighers.Weighers.applyWeighs
import org.jetbrains.kotlin.idea.util.positionContext.KotlinImportDirectivePositionContext

internal class K2ImportDirectivePackageMembersCompletionContributor : K2SimpleCompletionContributor<KotlinImportDirectivePositionContext>(
    KotlinImportDirectivePositionContext::class
) {
    @OptIn(KaExperimentalApi::class)
    context(_: KaSession, context: K2CompletionSectionContext<KotlinImportDirectivePositionContext>)
    override fun complete() {
        val positionContext = context.positionContext
        positionContext.resolveReceiverToSymbols()
            .mapNotNull { it.staticScope }
            .flatMap { scopeWithKind ->
                val scope = scopeWithKind.scope

                scope.classifiers(context.completionContext.scopeNameFilter)
                    .filter { context.visibilityChecker.isVisible(it, positionContext) }
                    .mapNotNull { symbol ->
                        KotlinFirLookupElementFactory.createClassifierLookupElement(symbol)?.applyWeighs(
                            symbolWithOrigin = KtSymbolWithOrigin(symbol, scopeWithKind.kind)
                        )
                    } + scope.callables(context.completionContext.scopeNameFilter)
                    .filter { context.visibilityChecker.isVisible(it, positionContext) }
                    .map { it.asSignature() }
                    .flatMap {
                        createCallableLookupElements(
                            signature = it,
                            options = CallableInsertionOptions(ImportStrategy.DoNothing, CallableInsertionStrategy.AsIdentifier),
                            scopeKind = scopeWithKind.kind,
                        )
                    }
            }.forEach { addElement(it) }
    }
}