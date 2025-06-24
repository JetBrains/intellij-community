// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.checkers

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.components.KaCompletionCandidateChecker
import org.jetbrains.kotlin.analysis.api.components.KaCompletionExtensionCandidateChecker
import org.jetbrains.kotlin.analysis.api.components.KaExtensionApplicabilityResult
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

@OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
internal class KtCompletionExtensionCandidateChecker private constructor(
    private val delegate: KaCompletionExtensionCandidateChecker
) : KaCompletionExtensionCandidateChecker {

    /**
     * Cached applicability results for callable extension symbols.
     * The cache **must not outlive the lifetime of a single completion session**.
     *
     * If an extension is applicable but some of its type parameters are substituted to error types, then multiple calls to
     * [computeApplicability] produce unequal substitutors, and subsequently unequal signatures, because
     * error types are considered equal only if their underlying types are referentially equal, so we need to use [cache] in order
     * to avoid unexpected unequal signatures.
     *
     * The cache also helps to avoid recalculation of applicability for extensions which are suggested twice:
     * the first time while processing the scope context and the second time while processing callables from indexes.
     */
    private val cache: MutableMap<KaCallableSymbol, KaExtensionApplicabilityResult> = mutableMapOf()

    override val token: KaLifetimeToken
        get() = delegate.token

    override fun computeApplicability(candidate: KaCallableSymbol): KaExtensionApplicabilityResult =
        cache.computeIfAbsent(candidate) {
            delegate.computeApplicability(candidate)
        }

    companion object {

        context(KaCompletionCandidateChecker)
        fun create(
            originalFile: KtFile,
            nameExpression: KtSimpleNameExpression,
            explicitReceiver: KtExpression? = null,
        ): KtCompletionExtensionCandidateChecker =
            createExtensionCandidateChecker(
                originalFile = originalFile,
                nameExpression = nameExpression,
                explicitReceiver = explicitReceiver,
            ).let { KtCompletionExtensionCandidateChecker(it) }
    }
}
