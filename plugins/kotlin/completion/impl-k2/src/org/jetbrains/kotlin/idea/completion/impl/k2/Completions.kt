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
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {
    context(KtAnalysisSession)
    fun complete(
        factory: FirCompletionContributorFactory,
        positionContext: FirRawPositionCompletionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        when (positionContext) {
            is FirExpressionNameReferencePositionContext -> if (positionContext.allowsOnlyNamedArguments()) {
                complete(factory.namedArgumentContributor(0), positionContext, weighingContext, sessionParameters)
            } else {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.namedArgumentContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.callableContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.classifierContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.packageCompletionContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is FirSuperReceiverNameReferencePositionContext -> {
                complete(factory.superMemberContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirTypeNameReferencePositionContext -> {
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

            is FirAnnotationTypeNameReferencePositionContext -> {
                complete(factory.annotationsContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.keywordContributor(1), positionContext, weighingContext, sessionParameters)
                complete(factory.packageCompletionContributor(2), positionContext, weighingContext, sessionParameters)
            }

            is FirSuperTypeCallNameReferencePositionContext -> {
                complete(factory.superEntryContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirImportDirectivePositionContext -> {
                complete(factory.packageCompletionContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.importDirectivePackageMembersContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirPackageDirectivePositionContext -> {
                complete(factory.packageCompletionContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirTypeConstraintNameInWhereClausePositionContext -> {
                complete(factory.typeParameterConstraintNameInWhereClauseContributor(), positionContext, weighingContext, sessionParameters)
            }

            is FirMemberDeclarationExpectedPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirUnknownPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirClassifierNamePositionContext -> {
                complete(factory.classifierNameContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.declarationFromUnresolvedNameContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is FirWithSubjectEntryPositionContext -> {
                complete(factory.whenWithSubjectConditionContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.callableContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is FirCallableReferencePositionContext -> {
                complete(factory.classReferenceContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.callableReferenceContributor(1), positionContext, weighingContext, sessionParameters)
                complete(factory.classifierReferenceContributor(1), positionContext, weighingContext, sessionParameters)
            }

            is FirInfixCallPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.infixCallableContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is FirSimpleParameterPositionContext -> {
                // for parameter declaration
                complete(factory.declarationFromUnresolvedNameContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                complete(factory.declarationFromUnresolvedNameContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.declarationFromOverridableMembersContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.keywordContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirKDocParameterNamePositionContext -> {
                complete(factory.kDocParameterNameContributor(0), positionContext, weighingContext, sessionParameters)
            }

            is FirKDocLinkNamePositionContext -> {
                complete(factory.kDocCallableContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.classifierContributor(0), positionContext, weighingContext, sessionParameters)
                complete(factory.packageCompletionContributor(1), positionContext, weighingContext, sessionParameters)
            }
        }
    }

    context(KtAnalysisSession)
    fun createWeighingContext(
        basicContext: FirBasicCompletionContext,
        positionContext: FirRawPositionCompletionContext
    ): WeighingContext = when (positionContext) {
        is FirSuperReceiverNameReferencePositionContext -> {
            val expectedType = positionContext.nameExpression.getExpectedType()
            val receiver = positionContext.superExpression

            // Implicit receivers do not match for this position completion context.
            WeighingContext.createWeighingContext(receiver, expectedType, implicitReceivers = emptyList(), positionContext.position)
        }

        is FirWithSubjectEntryPositionContext -> {
            val subjectReference = (positionContext.subjectExpression as? KtSimpleNameExpression)?.mainReference
            val symbolsToSkip = setOfNotNull(subjectReference?.resolveToSymbol())
            createWeighingContextForNameReference(basicContext, positionContext, symbolsToSkip)
        }

        is FirExpressionNameReferencePositionContext -> createWeighingContextForNameReference(basicContext, positionContext)
        is FirInfixCallPositionContext -> createWeighingContextForNameReference(basicContext, positionContext)

        else -> WeighingContext.createEmptyWeighingContext(positionContext.position)
    }

    context(KtAnalysisSession)
    private fun createWeighingContextForNameReference(
        basicContext: FirBasicCompletionContext,
        positionContext: FirNameReferencePositionContext,
        symbolsToSkip: Set<KtSymbol> = emptySet(),
    ): WeighingContext {
        val expectedType = positionContext.nameExpression.getExpectedType()
        val receiver = positionContext.explicitReceiver
        val implicitReceivers = basicContext.originalKtFile.getScopeContextForPosition(positionContext.nameExpression).implicitReceivers

        return WeighingContext.createWeighingContext(receiver, expectedType, implicitReceivers, positionContext.position, symbolsToSkip)
    }
}

context(KtAnalysisSession)
private fun FirExpressionNameReferencePositionContext.allowsOnlyNamedArguments(): Boolean {
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