// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
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

                    Completions.complete(
                        parameters = parameters,
                        positionContext = KotlinExpressionNameReferencePositionContext(nameExpression),
                        resultSet = result.withPrefixMatcher(ExactPrefixMatcher(nameExpression.text)),
                        before = { nameExpression.mainReference.resolveToSymbols().isEmpty() },
                    )
                }
            }
        )
    }

    private class ExactPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {

        override fun prefixMatches(name: String): Boolean = name == prefix

        override fun cloneWithPrefix(prefix: String): PrefixMatcher = this
    }
}