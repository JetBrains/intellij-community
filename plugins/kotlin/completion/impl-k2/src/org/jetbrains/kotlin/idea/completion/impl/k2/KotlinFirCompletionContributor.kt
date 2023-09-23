// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.completion.api.CompletionDummyIdentifierProviderService
import org.jetbrains.kotlin.idea.completion.context.*
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirPositionCompletionContextDetector
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.completion.contributors.FirCompletionContributorFactory
import org.jetbrains.kotlin.idea.completion.weighers.Weighers
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.isMultiPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

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
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        @Suppress("NAME_SHADOWING") val parameters = KotlinFirCompletionParametersProvider.provide(parameters)

        if (shouldSuppressCompletion(parameters.ijParameters, result.prefixMatcher)) return
        val positionContext = FirPositionCompletionContextDetector.detect(parameters.ijParameters.position)
        val resultSet = createResultSet(parameters, positionContext, result)

        val basicContext = FirBasicCompletionContext.createFromParameters(parameters, resultSet) ?: return



        FirPositionCompletionContextDetector.analyzeInContext(basicContext, positionContext) {
            recordOriginalFile(basicContext)
            complete(basicContext, positionContext)
        }
    }


    context(KtAnalysisSession)
    private fun complete(
        basicContext: FirBasicCompletionContext,
        positionContext: FirRawPositionCompletionContext,
    ) {
        val factory = FirCompletionContributorFactory(basicContext)
        with(Completions) {
            val weighingContext = createWeighingContext(basicContext, positionContext)
            val sessionParameters = FirCompletionSessionParameters(basicContext, positionContext)
            complete(factory, positionContext, weighingContext, sessionParameters)
        }
    }


    context(KtAnalysisSession)
    private fun recordOriginalFile(basicCompletionContext: FirBasicCompletionContext) {
        val originalFile = basicCompletionContext.originalKtFile
        val fakeFile = basicCompletionContext.fakeKtFile
        fakeFile.recordOriginalKtFile(originalFile)
    }

    private fun createResultSet(
        parameters: KotlinFirCompletionParameters,
        positionContext: FirRawPositionCompletionContext,
        result: CompletionResultSet
    ): CompletionResultSet {
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.ijParameters.position.containingFile,
            parameters.ijParameters.offset,
            kotlinIdentifierPartPattern(),
            kotlinIdentifierStartPattern()
        )
        return result.withRelevanceSorter(createSorter(parameters.ijParameters, positionContext, result)).withPrefixMatcher(prefix)
    }

    private fun createSorter(
        parameters: CompletionParameters,
        positionContext: FirRawPositionCompletionContext,
        result: CompletionResultSet
    ): CompletionSorter = CompletionSorter.defaultSorter(parameters, result.prefixMatcher)
        .let { Weighers.addWeighersToCompletionSorter(it, positionContext) }

    private val AFTER_NUMBER_LITERAL = PsiJavaPatterns.psiElement().afterLeafSkipping(
        PsiJavaPatterns.psiElement().withText(""),
        PsiJavaPatterns.psiElement().withElementType(PsiJavaPatterns.elementType().oneOf(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL))
    )
    private val AFTER_INTEGER_LITERAL_AND_DOT = PsiJavaPatterns.psiElement().afterLeafSkipping(
        PsiJavaPatterns.psiElement().withText("."),
        PsiJavaPatterns.psiElement().withElementType(PsiJavaPatterns.elementType().oneOf(KtTokens.INTEGER_LITERAL))
    )

    private fun shouldSuppressCompletion(parameters: CompletionParameters, prefixMatcher: PrefixMatcher): Boolean {
        val position = parameters.position
        val invocationCount = parameters.invocationCount

        // no completion inside number literals
        if (AFTER_NUMBER_LITERAL.accepts(position)) return true

        // no completion auto-popup after integer and dot
        if (invocationCount == 0 && prefixMatcher.prefix.isEmpty() && AFTER_INTEGER_LITERAL_AND_DOT.accepts(position)) return true

        return false
    }
}

internal data class FirCompletionSessionParameters(
    private val basicContext: FirBasicCompletionContext,
    private val positionContext: FirRawPositionCompletionContext,
) {
    private val languageVersionSettings = basicContext.project.languageVersionSettings
    val excludeEnumEntries: Boolean = !languageVersionSettings.supportsFeature(LanguageFeature.EnumEntries)

    val allowSyntheticJavaProperties: Boolean = positionContext !is FirKDocNameReferencePositionContext &&
            (positionContext !is FirCallableReferencePositionContext || languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties))

    val allowJavaGettersAndSetters: Boolean = !allowSyntheticJavaProperties || basicContext.parameters.invocationCount > 1
    val allowExpectedDeclarations: Boolean = basicContext.originalKtFile.moduleInfo.platform.isMultiPlatform()

    val allowClassifiersAndPackagesForPossibleExtensionCallables: Boolean
        get() {
            val declaration = (positionContext as? FirTypeNameReferencePositionContext)?.typeReference?.parent ?: return true
            return !(basicContext.parameters.invocationCount == 0
                    && (declaration is KtNamedFunction || declaration is KtProperty)
                    && positionContext.explicitReceiver == null
                    && basicContext.prefixMatcher.prefix.firstOrNull()?.isLowerCase() == true)
        }
}
