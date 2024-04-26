// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.context.*
import org.jetbrains.kotlin.idea.completion.contributors.FirCompletionContributorFactory
import org.jetbrains.kotlin.idea.completion.contributors.complete
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {
    context(KtAnalysisSession)
    fun complete(
        factory: FirCompletionContributorFactory,
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> if (positionContext.allowsOnlyNamedArguments()) {
                complete(factory.namedArgumentContributor(0), positionContext, weighingContext, sessionParameters)
            } else {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.namedArgumentContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.callableContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.classifierContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.packageCompletionContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is KotlinSuperReceiverNameReferencePositionContext -> {
                complete(factory.superMemberContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinTypeNameReferencePositionContext -> {
                if (sessionParameters.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    complete(factory.classifierContributor(0), positionContext, weighingContext, sessionParameters)
                }
                complete(factory.keywordContributor(1), positionContext, weighingContext, sessionParameters)
                if (sessionParameters.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    complete(factory.packageCompletionContributor(2), positionContext, weighingContext, sessionParameters)
                }
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                complete(factory.declarationFromUnresolvedNameContributor(1), positionContext, weighingContext, sessionParameters)
                complete(factory.declarationFromOverridableMembersContributor(1), positionContext, weighingContext, sessionParameters)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinAnnotationTypeNameReferencePositionContext -> {
                complete(factory.annotationsContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.keywordContributor(1), positionContext, weighingContext, sessionParameters)
                complete(factory.packageCompletionContributor(2), positionContext, weighingContext, sessionParameters)
            }

            is KotlinSuperTypeCallNameReferencePositionContext -> {
                complete(factory.superEntryContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinImportDirectivePositionContext -> {
                complete(factory.packageCompletionContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.importDirectivePackageMembersContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinPackageDirectivePositionContext -> {
                complete(factory.packageCompletionContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinTypeConstraintNameInWhereClausePositionContext -> {
                complete(factory.typeParameterConstraintNameInWhereClauseContributor(), positionContext, weighingContext, sessionParameters)
            }

            is KotlinMemberDeclarationExpectedPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinUnknownPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinClassifierNamePositionContext -> {
                complete(factory.classifierNameContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.declarationFromUnresolvedNameContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is KotlinWithSubjectEntryPositionContext -> {
                complete(factory.whenWithSubjectConditionContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.callableContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is KotlinCallableReferencePositionContext -> {
                complete(factory.classReferenceContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.callableReferenceContributor(1), positionContext, weighingContext, sessionParameters)
                complete(factory.classifierReferenceContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is KotlinInfixCallPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.infixCallableContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is KotlinSimpleParameterPositionContext -> {
                // for parameter declaration
                complete(factory.declarationFromUnresolvedNameContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KotlinPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                complete(factory.declarationFromUnresolvedNameContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.declarationFromOverridableMembersContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KDocParameterNamePositionContext -> {
                complete(factory.kDocParameterNameContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is KDocLinkNamePositionContext -> {
                complete(factory.kDocCallableContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.classifierContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.packageCompletionContributor(1), positionContext, weighingContext, sessionParameters)
            }
        }
    }

    context(KtAnalysisSession)
    fun createWeighingContext(
        basicContext: FirBasicCompletionContext,
        positionContext: KotlinRawPositionContext
    ): WeighingContext = when (positionContext) {
        is KotlinSuperReceiverNameReferencePositionContext -> {
            val expectedType = positionContext.nameExpression.getExpectedType()
            val receiver = positionContext.superExpression

            // Implicit receivers do not match for this position completion context.
            WeighingContext.createWeighingContext(
                basicContext,
                receiver,
                expectedType,
                implicitReceivers = emptyList(),
                positionContext.position
            )
        }

        is KotlinWithSubjectEntryPositionContext -> {
            val subjectReference = (positionContext.subjectExpression as? KtSimpleNameExpression)?.mainReference
            val symbolsToSkip = setOfNotNull(subjectReference?.resolveToSymbol())
            createWeighingContextForNameReference(basicContext, positionContext, symbolsToSkip)
        }

        is KotlinNameReferencePositionContext -> createWeighingContextForNameReference(basicContext, positionContext)
        else -> WeighingContext.createEmptyWeighingContext(basicContext, positionContext.position)
    }

    context(KtAnalysisSession)
    private fun createWeighingContextForNameReference(
        basicContext: FirBasicCompletionContext,
        positionContext: KotlinNameReferencePositionContext,
        symbolsToSkip: Set<KtSymbol> = emptySet(),
    ): WeighingContext {
        val expectedType = when (positionContext) {
            // during the sorting of completion suggestions expected type from position and actual types of suggestions are compared;
            // see `org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher`;
            // currently in case of callable references actual types are calculated incorrectly, which is why we don't use information
            // about expected type at all
            // TODO: calculate actual types for callable references correctly and use information about expected type
            is KotlinCallableReferencePositionContext -> null
            else -> positionContext.nameExpression.getExpectedType()
        }
        val receiver = positionContext.explicitReceiver
        val implicitReceivers = basicContext.originalKtFile.getScopeContextForPosition(positionContext.nameExpression).implicitReceivers

        return WeighingContext.createWeighingContext(
            basicContext,
            receiver,
            expectedType,
            implicitReceivers,
            positionContext.position,
            symbolsToSkip
        )
    }
}

context(KtAnalysisSession)
private fun KotlinExpressionNameReferencePositionContext.allowsOnlyNamedArguments(): Boolean {
    if (explicitReceiver != null) return false

    val valueArgument = findValueArgument(nameExpression) ?: return false
    val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return false
    val callElement = valueArgumentList.parent as? KtCallElement ?: return false

    if (valueArgument.getArgumentName() != null) return false

    val call = callElement.resolveCall()?.singleCallOrNull<KtFunctionCall<*>>() ?: return false

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