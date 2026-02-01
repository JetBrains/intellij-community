// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.completion.impl.k2.jfr.CompletionEvent
import org.jetbrains.kotlin.idea.completion.impl.k2.jfr.timeEvent
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class KotlinChainCompletionContributor : CompletionContributor() {

    init {
        if (!RegistryManager.getInstance().`is`("kotlin.k2.chain.completion.enabled"))
            throw ExtensionNotApplicableException.create()

        extend(
            /* type = */ CompletionType.BASIC,
            /* place = */ psiElement(KtTokens.IDENTIFIER)
                .afterLeaf(psiElement(KtTokens.DOT))
                .withParents(
                    KtNameReferenceExpression::class.java,
                    KtDotQualifiedExpression::class.java,
                ),
            /* provider = */ object : CompletionProvider<CompletionParameters>() {

                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val parameters = KotlinFirCompletionParameters.Original.create(parameters)
                        ?: return

                    val qualifiedExpression = parameters.position
                        .parent
                        ?.parent as? KtDotQualifiedExpression

                    val nameExpression = qualifiedExpression?.receiverExpression as? KtNameReferenceExpression
                        ?: return

                    analyze(parameters.completionFile) {
                        if (nameExpression.mainReference.resolveToSymbols().isNotEmpty()) {
                            // The receiver is resolved, therefore we do not run chain completion
                            return
                        }
                    }

                    CompletionEvent(isChainCompletion = true).timeEvent {
                        Completions.complete(
                            parameters = parameters,
                            positionContext = KotlinExpressionNameReferencePositionContext(nameExpression),
                            resultSet = result.withPrefixMatcher(ExactPrefixMatcher(nameExpression.text)),
                        )
                    }
                }
            }
        )
    }

    private class ExactPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {

        override fun prefixMatches(name: String): Boolean = name == prefix

        override fun cloneWithPrefix(prefix: String): PrefixMatcher = this
    }
}