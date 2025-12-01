// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaApplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaInapplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.*
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.fir.*
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.factories.ClassifierLookupObject
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.getAnnotationLiteralExpectedType
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext.Companion.getEqualityExpectedType
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render
import java.util.concurrent.CopyOnWriteArrayList

internal object Completions {

    /**
     * Returns whether any elements were added to the [resultSet].
     */
    fun complete(
        parameters: KotlinFirCompletionParameters,
        positionContext: KotlinRawPositionContext,
        resultSet: CompletionResultSet,
    ): Boolean {
        return if (Registry.`is`("kotlin.k2.parallel.completion.enabled", false)) {
            completeInParallel(parameters, positionContext, resultSet)
        } else {
            completeSequentially(parameters, positionContext, resultSet)
        }
    }

    private val contributors: List<K2CompletionContributor<*>> = listOf(
        K2ClassifierCompletionContributor(),
        K2ClassReferenceCompletionContributor(),
        K2KDocParameterNameContributor(),
        K2DeclarationFromOverridableMembersContributor(),
        K2ActualDeclarationContributor(),
        K2WhenWithSubjectConditionContributor(),
        K2SuperEntryContributor(),
        K2OperatorNameCompletionContributor(),
        K2TypeParameterConstraintNameInWhereClauseCompletionContributor(),
        K2SameAsFileClassifierNameCompletionContributor(),
        K2PackageCompletionContributor(),
        K2NamedArgumentCompletionContributor(),
        K2DeclarationFromUnresolvedNameContributor(),
        K2KeywordCompletionContributor(),
        K2ImportDirectivePackageMembersCompletionContributor(),
        K2SuperMemberCompletionContributor(),
        K2TrailingFunctionParameterNameCompletionContributorBase.All(),
        K2TrailingFunctionParameterNameCompletionContributorBase.Missing(),
        K2CallableCompletionContributor(),
        K2CallableReferenceCompletionContributor(),
        K2InfixCallableCompletionContributor(),
        K2KDocCallableCompletionContributor(),
        K2VariableOrParameterNameWithTypeCompletionContributor(),
    )

    /**
     * Completes using the new structure and the new K2 contributor structure
     */
    private fun <T : KotlinRawPositionContext> completeInParallel(
        parameters: KotlinFirCompletionParameters,
        positionContext: T,
        resultSet: CompletionResultSet,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val matchingContributors =
            contributors.filter { it.positionContextClass.isInstance(positionContext) } as List<K2CompletionContributor<T>>

        val sections = mutableListOf<K2CompletionSection<T>>()
        val completionContext = K2CompletionContext(parameters, resultSet, positionContext)
        for (contributor in matchingContributors) {
            // We make sure the type parameters match before, so this cast is safe.

            val setupScope = K2CompletionSetupScope(completionContext, contributor, sections)
            with(contributor) {
                if (setupScope.isAppropriatePosition()) {
                    setupScope.registerCompletions()
                }
            }
        }

        val completionRunner = K2CompletionRunner.getInstance(sections.size)

        // We make sure the type parameters match before, so this cast is safe.
        val completionRunnerResult = completionRunner.runCompletion(completionContext, sections)

        runChainCompletionIfNecessary(
            completionResult = completionRunnerResult,
            positionContext = positionContext,
            resultSet = resultSet,
            parameters = parameters
        )

        return completionRunnerResult.addedElements > 0
    }

    private fun runChainCompletionIfNecessary(
        completionResult: K2CompletionRunnerResult,
        positionContext: KotlinRawPositionContext,
        resultSet: CompletionResultSet,
        parameters: KotlinFirCompletionParameters,
    ) {
        if (completionResult.registeredChainContributors.isEmpty()) return
        if (positionContext !is KotlinNameReferencePositionContext) return
        if (!RegistryManager.getInstance().`is`("kotlin.k2.chain.completion.enabled")) return

        (positionContext.explicitReceiver as? KtExpression)?.let { receiver ->
            analyze(receiver) {
                if (receiver.resolveExpression() != null) {
                    // The explicit receiver is already resolved, no point in running chain completion
                    return@runChainCompletionIfNecessary
                }
            }
        }

        K2CompletionRunner.runChainCompletion(
            originalPositionContext = positionContext,
            completionResultSet = resultSet,
            parameters = parameters,
            chainCompletionContributors = completionResult.registeredChainContributors,
        )
    }


    /**
     * Completes using the old structure using the Fir contributors.
     */
    private fun completeSequentially(
        parameters: KotlinFirCompletionParameters,
        positionContext: KotlinRawPositionContext,
        resultSet: CompletionResultSet,
    ): Boolean = analyze(parameters.completionFile) {
        val weighingContext = when (positionContext) {
            is KotlinNameReferencePositionContext -> {
                val nameExpression = positionContext.nameExpression
                val expectedType = when {
                    // during the sorting of completion suggestions expected type from position and actual types of suggestions are compared;
                    // see `org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher`;
                    // currently in case of callable references actual types are calculated incorrectly, which is why we don't use information
                    // about expected type at all
                    // TODO: calculate actual types for callable references correctly and use information about expected type
                    positionContext is KotlinCallableReferencePositionContext -> null
                    nameExpression.expectedType != null -> nameExpression.expectedType
                    nameExpression.parent is KtBinaryExpression -> getEqualityExpectedType(nameExpression)
                    nameExpression.parent is KtCollectionLiteralExpression -> getAnnotationLiteralExpectedType(nameExpression)
                    else -> null
                }
                if (parameters.completionType == CompletionType.SMART
                    && expectedType == null
                ) return@analyze false // todo move out

                WeighingContext.create(parameters, positionContext, expectedType)
            }

            else -> WeighingContext.create(parameters, elementInCompletionFile = positionContext.position)
        }

        val contributors = CopyOnWriteArrayList<ChainCompletionContributor>() // if needed for the multithreaded completion
        val sink = LookupElementSink(resultSet, parameters) {
            contributors.addIfAbsent(it)
        }
        completeSequentially(positionContext, sink, weighingContext)

        if (positionContext is KotlinNameReferencePositionContext
            && contributors.isNotEmpty()
            && RegistryManager.getInstance().`is`("kotlin.k2.chain.completion.enabled")
        ) {
            runChainCompletion(positionContext, sink, contributors)
        }

        sink.addedElementCount > 0
    }

    context(_: KaSession)
    private fun completeSequentially(
        positionContext: KotlinRawPositionContext,
        sink: LookupElementSink,
        weighingContext: WeighingContext,
    ) {
        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> {
                if (positionContext.isAfterRangeOperator()) return
                FirTrailingFunctionParameterNameCompletionContributorBase.All(sink)
                    .complete(positionContext, weighingContext)
                if (positionContext.allowsOnlyNamedArguments()) {
                    FirNamedArgumentCompletionContributor(sink)
                        .complete(positionContext, weighingContext)
                } else {
                    FirKeywordCompletionContributor(sink)
                        .complete(positionContext, weighingContext)
                    FirNamedArgumentCompletionContributor(sink)
                        .complete(positionContext, weighingContext)
                    FirCallableCompletionContributor(sink, withTrailingLambda = true)
                        .complete(positionContext, weighingContext)
                    FirClassifierCompletionContributor(sink)
                        .complete(positionContext, weighingContext)
                    FirPackageCompletionContributor(sink, priority = 1)
                        .complete(positionContext, weighingContext)
                }
            }

            is KotlinSuperReceiverNameReferencePositionContext -> {
                FirSuperMemberCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinTypeNameReferencePositionContext -> {
                FirOperatorNameCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                val allowClassifiersAndPackagesForPossibleExtensionCallables =
                    !positionContext.hasNoExplicitReceiver()
                            || sink.parameters.invocationCount > 0
                            || sink.prefixMatcher.prefix.firstOrNull()?.isLowerCase() != true

                if (allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirClassifierCompletionContributor(sink)
                        .complete(positionContext, weighingContext)
                }
                FirKeywordCompletionContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
                if (allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirPackageCompletionContributor(sink, priority = 2)
                        .complete(positionContext, weighingContext)
                }
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                FirDeclarationFromUnresolvedNameContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirActualDeclarationContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinAnnotationTypeNameReferencePositionContext -> {
                FirAnnotationCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(sink, priority = 2)
                    .complete(positionContext, weighingContext)
            }

            is KotlinSuperTypeCallNameReferencePositionContext -> {
                FirSuperEntryContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinImportDirectivePositionContext -> {
                FirPackageCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirImportDirectivePackageMembersCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinPackageDirectivePositionContext -> {
                FirPackageCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinTypeConstraintNameInWhereClausePositionContext -> {
                FirTypeParameterConstraintNameInWhereClauseCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinMemberDeclarationExpectedPositionContext -> {
                FirKeywordCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinLabelReferencePositionContext -> {
                FirKeywordCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinUnknownPositionContext -> {
                if (positionContext.isAfterRangeToken()) return
                FirKeywordCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinClassifierNamePositionContext -> {
                FirSameAsFileClassifierNameCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromUnresolvedNameContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }

            is KotlinWithSubjectEntryPositionContext -> {
                FirWhenWithSubjectConditionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirClassifierCompletionContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirCallableCompletionContributor(sink, priority = 2)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(sink, priority = 3)
                    .complete(positionContext, weighingContext)
            }

            is KotlinCallableReferencePositionContext -> {
                FirClassReferenceCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirCallableReferenceCompletionContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirClassifierReferenceCompletionContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }

            is KotlinInfixCallPositionContext -> {
                FirKeywordCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirInfixCallableCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinOperatorCallPositionContext,
            is KotlinIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is KotlinSimpleParameterPositionContext -> {
                FirTrailingFunctionParameterNameCompletionContributorBase.Missing(sink)
                    .complete(positionContext, weighingContext)
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(sink)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KDocParameterNamePositionContext -> {
                FirKDocParameterNameContributor(sink)
                    .complete(positionContext, weighingContext)
            }

            is KDocLinkNamePositionContext -> {
                FirKDocParameterNameContributor(sink)
                    .complete(positionContext, weighingContext)
                FirKDocCallableCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirClassifierCompletionContributor(sink)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }
        }
    }

    @OptIn(KaImplementationDetail::class)
    private fun runChainCompletion(
        positionContext: KotlinNameReferencePositionContext,
        sink: LookupElementSink,
        contributors: List<ChainCompletionContributor>,
    ) {
        val explicitReceiver = positionContext.explicitReceiver ?: return

        sink.runRemainingContributors(sink.parameters.delegate) { completionResult ->
            val lookupElement = completionResult.lookupElement
            val classifierLookupObject = lookupElement.`object` as? ClassifierLookupObject
            val nameToImport = when (val importStrategy = classifierLookupObject?.importingStrategy) {
                is ImportStrategy.AddImport -> importStrategy.nameToImport
                is ImportStrategy.InsertFqNameAndShorten -> importStrategy.fqName
                else -> null
            }

            if (nameToImport == null) {
                sink.passResult(completionResult)
                return@runRemainingContributors
            }

            val expression = KtPsiFactory.contextual(explicitReceiver)
                .createExpression(nameToImport.render() + "." + positionContext.nameExpression.text) as KtDotQualifiedExpression

            val receiverExpression = expression.receiverExpression as? KtDotQualifiedExpression
            val nameExpression = expression.selectorExpression as? KtNameReferenceExpression

            if (receiverExpression == null
                || nameExpression == null
            ) {
                sink.passResult(completionResult)
                return@runRemainingContributors
            }

            analyze(nameExpression) {
                val positionContext = KotlinExpressionNameReferencePositionContext(nameExpression)
                val importingStrategy = ImportStrategy.AddImport(nameToImport)

                val lookupElements = contributors.asSequence()
                    .flatMap { contributor ->
                        contributor.createChainedLookupElements(positionContext, receiverExpression, importingStrategy)
                    }.asIterable()
                sink.addAllElements(lookupElements)
            }
        }
    }
}

internal fun KotlinUnknownPositionContext.isAfterRangeToken(): Boolean {
    val errorParent = position.parent as? PsiErrorElement
        ?: return false

    val prevSibling = errorParent.prevSibling
    val rangeToPrefix = KtTokens.RANGE.value
    return prevSibling is PsiErrorElement && prevSibling.textMatches(rangeToPrefix)
            || errorParent.text.startsWith(rangeToPrefix)
}

/**
 * Determines whether the current context occurs after a double dot (`..`) operator, excluding `..`
 * usages related to `rangeTo`.
 * It is used for compatibility with command completion.
 *
 * @return `true` if the context is after a double dot (`..`) not associated with a `rangeTo` operation,
 *         otherwise `false`.
 */
context(_: KaSession)
internal fun KotlinRawPositionContext.isAfterRangeOperator(): Boolean {
    if (this !is KotlinExpressionNameReferencePositionContext) return false
    val binaryExpression = nameExpression.parent as? KtBinaryExpression
        ?: return false

    if (binaryExpression.operationToken != KtTokens.RANGE) return false

    return binaryExpression.operationReference
        .resolveToCallCandidates()
        .none { candidateInfo ->
            when (candidateInfo) {
                is KaApplicableCallCandidateInfo -> true
                is KaInapplicableCallCandidateInfo -> candidateInfo.diagnostic is KaFirDiagnostic.InapplicableCandidate
            }
        }
}

context(_: KaSession)
internal fun KotlinRawPositionContext.allowsOnlyNamedArguments(): Boolean {
    if (this !is KotlinExpressionNameReferencePositionContext) return false
    if (explicitReceiver != null) return false

    val valueArgument = findValueArgument(nameExpression) ?: return false
    val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return false
    val callElement = valueArgumentList.parent as? KtCallElement ?: return false

    if (valueArgument.getArgumentName() != null) return false

    val call = callElement.resolveToCall()?.singleCallOrNull<KaFunctionCall<*>>() ?: return false

    if (CallParameterInfoProvider.isJavaArgumentWithNonDefaultName(
            call.partiallyAppliedSymbol.signature,
            call.argumentMapping,
            valueArgument
        )
    ) return true

    val firstArgumentInNamedMode = CallParameterInfoProvider.firstArgumentInNamedMode(
        callElement,
        call.partiallyAppliedSymbol.signature,
        call.argumentMapping,
        callElement.languageVersionSettings
    ) ?: return false

    return with(valueArgumentList.arguments) { indexOf(valueArgument) >= indexOf(firstArgumentInNamedMode) }
}

internal fun KotlinTypeNameReferencePositionContext.hasNoExplicitReceiver(): Boolean {
    val declaration = typeReference?.parent
        ?: return false

    return (declaration is KtNamedFunction || declaration is KtProperty)
            && explicitReceiver == null
}
