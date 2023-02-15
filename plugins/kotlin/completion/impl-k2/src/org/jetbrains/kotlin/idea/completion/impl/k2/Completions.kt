// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.context.*
import org.jetbrains.kotlin.idea.completion.contributors.FirCompletionContributorFactory
import org.jetbrains.kotlin.idea.completion.contributors.complete
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {
    fun KtAnalysisSession.complete(
        factory: FirCompletionContributorFactory,
        positionContext: FirRawPositionCompletionContext,
        weighingContext: WeighingContext
    ) {
        when (positionContext) {
            is FirExpressionNameReferencePositionContext -> if (positionContext.allowsOnlyNamedArguments()) {
                complete(factory.namedArgumentContributor(0), positionContext, weighingContext)
            } else {
                complete(factory.keywordContributor(0), positionContext, weighingContext)
                complete(factory.namedArgumentContributor(0), positionContext, weighingContext)
                complete(factory.callableContributor(0), positionContext, weighingContext)
                complete(factory.classifierContributor(0), positionContext, weighingContext)
                complete(factory.packageCompletionContributor(1), positionContext, weighingContext)
            }

            is FirSuperReceiverNameReferencePositionContext -> {
                complete(factory.superMemberContributor(0), positionContext, weighingContext)
            }

            is FirTypeNameReferencePositionContext -> {
                complete(factory.classifierContributor(0), positionContext, weighingContext)
                complete(factory.keywordContributor(1), positionContext, weighingContext)
                complete(factory.packageCompletionContributor(2), positionContext, weighingContext)
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                complete(factory.declarationFromUnresolvedNameContributor(1), positionContext, weighingContext)
                complete(factory.declarationFromOverridableMembersContributor(1), positionContext, weighingContext)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext)
            }

            is FirAnnotationTypeNameReferencePositionContext -> {
                complete(factory.annotationsContributor(0), positionContext, weighingContext)
                complete(factory.keywordContributor(1), positionContext, weighingContext)
                complete(factory.packageCompletionContributor(2), positionContext, weighingContext)
            }

            is FirSuperTypeCallNameReferencePositionContext -> {
                complete(factory.superEntryContributor(0), positionContext, weighingContext)
            }

            is FirImportDirectivePositionContext -> {
                complete(factory.packageCompletionContributor(0), positionContext, weighingContext)
                complete(factory.importDirectivePackageMembersContributor(0), positionContext, weighingContext)
            }

            is FirPackageDirectivePositionContext -> {
                complete(factory.packageCompletionContributor(0), positionContext, weighingContext)
            }

            is FirTypeConstraintNameInWhereClausePositionContext -> {
                complete(factory.typeParameterConstraintNameInWhereClauseContributor(0), positionContext, weighingContext)
            }

            is FirMemberDeclarationExpectedPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext)
            }

            is FirUnknownPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext)
            }

            is FirClassifierNamePositionContext -> {
                complete(factory.classifierNameContributor(0), positionContext, weighingContext)
                complete(factory.declarationFromUnresolvedNameContributor(1), positionContext, weighingContext)
            }

            is FirWithSubjectEntryPositionContext -> {
                complete(factory.whenWithSubjectConditionContributor(0), positionContext, weighingContext)
            }

            is FirCallableReferencePositionContext -> {
                complete(factory.classReferenceContributor(0), positionContext, weighingContext)
                complete(factory.callableReferenceContributor(1), positionContext, weighingContext)
                complete(factory.classifierReferenceContributor(1), positionContext, weighingContext)
            }

            is FirInfixCallPositionContext -> {
                complete(factory.keywordContributor(0), positionContext, weighingContext)
                complete(factory.infixCallableContributor(0), positionContext, weighingContext)
            }

            is FirIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is FirSimpleParameterPositionContext -> {
                complete(factory.declarationFromUnresolvedNameContributor(0), positionContext, weighingContext) // for parameter declaration
                complete(factory.keywordContributor(0), positionContext, weighingContext)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext)
            }

            is FirPrimaryConstructorParameterPositionContext -> {
                complete(factory.declarationFromUnresolvedNameContributor(0), positionContext, weighingContext) // for parameter declaration
                complete(factory.declarationFromOverridableMembersContributor(0), positionContext, weighingContext)
                complete(factory.keywordContributor(0), positionContext, weighingContext)
                complete(factory.variableOrParameterNameWithTypeContributor(0), positionContext, weighingContext)
            }
        }
    }

    fun KtAnalysisSession.createWeighingContext(
        basicContext: FirBasicCompletionContext,
        positionContext: FirRawPositionCompletionContext
    ): WeighingContext = when (positionContext) {
        is FirSuperReceiverNameReferencePositionContext -> {
            val expectedType = positionContext.nameExpression.getExpectedType()
            val receiver = positionContext.superExpression

            // Implicit receivers do not match for this position completion context.
            WeighingContext.createWeighingContext(receiver, expectedType, implicitReceivers = emptyList(), basicContext.fakeKtFile)
        }

        is FirExpressionNameReferencePositionContext -> createWeighingContextForNameReference(basicContext, positionContext)
        is FirInfixCallPositionContext -> createWeighingContextForNameReference(basicContext, positionContext)

        else -> WeighingContext.createEmptyWeighingContext(basicContext.fakeKtFile)
    }

    private fun KtAnalysisSession.createWeighingContextForNameReference(
        basicContext: FirBasicCompletionContext,
        positionContext: FirNameReferencePositionContext
    ): WeighingContext {
        val expectedType = positionContext.nameExpression.getExpectedType()
        val receiver = positionContext.explicitReceiver
        val implicitReceivers = basicContext.originalKtFile.getScopeContextForPosition(positionContext.nameExpression).implicitReceivers

        return WeighingContext.createWeighingContext(receiver, expectedType, implicitReceivers, basicContext.fakeKtFile)
    }
}

context(KtAnalysisSession)
private fun FirExpressionNameReferencePositionContext.allowsOnlyNamedArguments(): Boolean {
    if (explicitReceiver != null) return false

    val valueArgument = findValueArgument(nameExpression) ?: return false
    val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return false
    val callElement = valueArgumentList.parent as? KtCallElement ?: return false

    if (valueArgument.getArgumentName() != null) return false

    val call = callElement.resolveCall().singleCallOrNull<KtFunctionCall<*>>() ?: return false
    val firstArgumentInNamedMode = CallParameterInfoProvider.firstArgumentInNamedMode(
        callElement,
        call.partiallyAppliedSymbol.signature,
        call.argumentMapping,
        callElement.languageVersionSettings
    ) ?: return false

    return with(valueArgumentList.arguments) { indexOf(valueArgument) >= indexOf(firstArgumentInNamedMode) }
}