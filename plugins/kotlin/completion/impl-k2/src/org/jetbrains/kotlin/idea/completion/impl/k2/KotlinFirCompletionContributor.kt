// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import com.intellij.util.applyIf
import org.jetbrains.kotlin.idea.completion.api.CompletionDummyIdentifierProviderService
import org.jetbrains.kotlin.idea.completion.impl.k2.Completions
import org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher.MatchesExpectedType
import org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher.matchesExpectedType
import org.jetbrains.kotlin.idea.completion.weighers.Weighers.applyWeighers
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPositionContextDetector
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile

class KotlinFirCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement(), KotlinFirCompletionProvider)

        // add tag completion in KDoc
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(StandardPatterns.or(psiElement(KDocTokens.LEADING_ASTERISK), psiElement(KDocTokens.START))),
            KDocTagCompletionProvider
        )
        extend(CompletionType.BASIC, psiElement(KDocTokens.TAG_NAME), KDocTagCompletionProvider)

        if (RegistryManager.getInstance().`is`("kotlin.k2.smart.completion.enabled")) {
            extend(
                CompletionType.SMART,
                psiElement(),
                KotlinFirCompletionProvider,
            )
        }
    }

    override fun beforeCompletion(context: CompletionInitializationContext) {
        val psiFile = context.file
        if (psiFile !is KtFile) return

        val identifierProviderService = CompletionDummyIdentifierProviderService.getInstance()

        correctPositionAndDummyIdentifier(identifierProviderService, context)
    }

    private fun correctPositionAndDummyIdentifier(
        identifierProviderService: CompletionDummyIdentifierProviderService,
        context: CompletionInitializationContext
    ) {
        // If replacement context is not "modified" externally then `com.intellij.codeInsight.completion.CompletionProgressIndicator`
        // searches for the reference at caret and on Tab replaces the whole reference, which in case of completion in Kotlin leads to bugs
        // such as KTIJ-26872.
        context.markReplacementOffsetAsModified()

        val dummyIdentifierCorrected = identifierProviderService.correctPositionForStringTemplateEntry(context)
        if (dummyIdentifierCorrected) {
            return
        }

        context.dummyIdentifier = identifierProviderService.provideDummyIdentifier(context)

        identifierProviderService.correctPositionForParameter(context)
    }
}

private object KotlinFirCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        @Suppress("NAME_SHADOWING") val parameters = KotlinFirCompletionParameters.create(parameters)
            ?: return
        val position = parameters.position

        // no completion inside number literals
        if (AFTER_NUMBER_LITERAL.accepts(position)) return
        val positionContext = KotlinPositionContextDetector.detect(position)
        val resultSet = result
            .withRelevanceSorter(parameters, positionContext)
            .withPrefixMatcher(parameters)

        val addedResults = Completions.complete(
            parameters = parameters,
            positionContext = positionContext,
            resultSet = resultSet,
        )

        // If we have not found any results and we have an invocation count 1, we want to re-run completion because
        // it will also start looking in nested objects etc.
        if (!addedResults && parameters.invocationCount == 1) {
            val newParameters = KotlinFirCompletionParameters.Original.create(parameters.delegate.withInvocationCount(2)) ?: return
            Completions.complete(newParameters, positionContext, resultSet)
        }
    }

    private fun CompletionResultSet.withPrefixMatcher(
        parameters: KotlinFirCompletionParameters,
    ): CompletionResultSet {
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.completionFile,
            parameters.offset,
            kotlinIdentifierPartPattern(),
            kotlinIdentifierStartPattern(),
        )

        return withPrefixMatcher(prefix)
            .applyIf(parameters.completionType == CompletionType.SMART) {
                withPrefixMatcher(SmartCompletionPrefixMatcher(prefixMatcher))
            }
    }

    private fun CompletionResultSet.withRelevanceSorter(
        parameters: KotlinFirCompletionParameters,
        positionContext: KotlinRawPositionContext,
    ): CompletionResultSet {
        val sorter = CompletionSorter.defaultSorter(parameters.delegate, prefixMatcher)
            .applyWeighers(positionContext)

        return withRelevanceSorter(sorter)
    }

    private val AFTER_NUMBER_LITERAL = PsiJavaPatterns.psiElement().afterLeafSkipping(
        PsiJavaPatterns.psiElement().withText(""),
        PsiJavaPatterns.psiElement().withElementType(PsiJavaPatterns.elementType().oneOf(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL))
    )

    private class SmartCompletionPrefixMatcher(
        private val delegate: PrefixMatcher,
    ) : PrefixMatcher(delegate.prefix) {

        override fun prefixMatches(element: LookupElement): Boolean =
            when (element.matchesExpectedType) {
                MatchesExpectedType.MATCHES_PREFERRED,
                MatchesExpectedType.MATCHES -> true

                else -> false
            } && super.prefixMatches(element)

        override fun prefixMatches(name: String): Boolean =
            delegate.prefixMatches(name)

        override fun cloneWithPrefix(prefix: String): PrefixMatcher =
            SmartCompletionPrefixMatcher(delegate.cloneWithPrefix(prefix))
    }
}
