// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.context.*
import org.jetbrains.kotlin.idea.completion.contributors.FirCompletionContributorFactory
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.FirCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.withPolicyController
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {

    context(KaSession)
    fun complete(
        basicContext: FirBasicCompletionContext,
        positionContext: KotlinRawPositionContext,
        policyController: PolicyController,
    ) {
        val factory = FirCompletionContributorFactory(basicContext)
        val weighingContext = createWeighingContext(basicContext, positionContext)
        val sessionParameters = FirCompletionSessionParameters(basicContext, positionContext)

        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> if (positionContext.allowsOnlyNamedArguments()) {
                factory.namedArgumentContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            } else {
                factory.keywordContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.namedArgumentContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.callableContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.classifierContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.packageCompletionContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinSuperReceiverNameReferencePositionContext -> {
                factory.superMemberContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinTypeNameReferencePositionContext -> {
                if (sessionParameters.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    factory.classifierContributor(0)
                        .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                }
                factory.keywordContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                if (sessionParameters.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    factory.packageCompletionContributor(2)
                        .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                }
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                factory.declarationFromUnresolvedNameContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.declarationFromOverridableMembersContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.variableOrParameterNameWithTypeContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinAnnotationTypeNameReferencePositionContext -> {
                factory.annotationsContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.keywordContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.packageCompletionContributor(2)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinSuperTypeCallNameReferencePositionContext -> {
                factory.superEntryContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinImportDirectivePositionContext -> {
                factory.packageCompletionContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.importDirectivePackageMembersContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinPackageDirectivePositionContext -> {
                factory.packageCompletionContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinTypeConstraintNameInWhereClausePositionContext -> {
                factory.typeParameterConstraintNameInWhereClauseContributor()
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinMemberDeclarationExpectedPositionContext -> {
                factory.keywordContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinLabelReferencePositionContext -> {
                factory.keywordContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinUnknownPositionContext -> {
                factory.keywordContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinClassifierNamePositionContext -> {
                factory.classifierNameContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.declarationFromUnresolvedNameContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinWithSubjectEntryPositionContext -> {
                factory.whenWithSubjectConditionContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.callableContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinCallableReferencePositionContext -> {
                factory.classReferenceContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.callableReferenceContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.classifierReferenceContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinInfixCallPositionContext -> {
                factory.keywordContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.infixCallableContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is KotlinSimpleParameterPositionContext -> {
                // for parameter declaration
                factory.declarationFromUnresolvedNameContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.keywordContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.variableOrParameterNameWithTypeContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                factory.declarationFromUnresolvedNameContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.declarationFromOverridableMembersContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.keywordContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.variableOrParameterNameWithTypeContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KDocParameterNamePositionContext -> {
                factory.kDocParameterNameContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KDocLinkNamePositionContext -> {
                factory.kDocCallableContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.classifierContributor(0)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                factory.packageCompletionContributor(1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }
        }
    }

    context(KaSession)
    private fun createWeighingContext(
        basicContext: FirBasicCompletionContext,
        positionContext: KotlinRawPositionContext
    ): WeighingContext = when (positionContext) {
        is KotlinSuperReceiverNameReferencePositionContext ->
            // Implicit receivers do not match for this position completion context.
            WeighingContext.createWeighingContext(
                basicContext = basicContext,
                receiver = positionContext.superExpression,
                expectedType = positionContext.nameExpression.expectedType,
                implicitReceivers = emptyList(),
                positionInFakeCompletionFile = positionContext.position,
            )

        is KotlinWithSubjectEntryPositionContext -> {
            val subjectReference = (positionContext.subjectExpression as? KtSimpleNameExpression)?.mainReference
            val symbolsToSkip = setOfNotNull(subjectReference?.resolveToSymbol())
            createWeighingContextForNameReference(basicContext, positionContext, symbolsToSkip)
        }

        is KotlinNameReferencePositionContext -> createWeighingContextForNameReference(basicContext, positionContext)
        else -> WeighingContext.createEmptyWeighingContext(basicContext, positionContext.position)
    }

    context(KaSession)
    private fun <C : KotlinRawPositionContext> FirCompletionContributor<C>.completeWithPolicyController(
        policyController: PolicyController,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        withPolicyController(policyController).complete(
            positionContext = @Suppress("UNCHECKED_CAST") (sessionParameters.positionContext as C), // TODO address dirty cast
            weighingContext = weighingContext,
            sessionParameters = sessionParameters,
        )
    }

    context(KaSession)
    private fun createWeighingContextForNameReference(
        basicContext: FirBasicCompletionContext,
        positionContext: KotlinNameReferencePositionContext,
        symbolsToSkip: Set<KaSymbol> = emptySet(),
    ): WeighingContext {
        val expectedType = when (positionContext) {
            // during the sorting of completion suggestions expected type from position and actual types of suggestions are compared;
            // see `org.jetbrains.kotlin.idea.completion.weighers.ExpectedTypeWeigher`;
            // currently in case of callable references actual types are calculated incorrectly, which is why we don't use information
            // about expected type at all
            // TODO: calculate actual types for callable references correctly and use information about expected type
            is KotlinCallableReferencePositionContext -> null
            else -> positionContext.nameExpression.expectedType
        }
        val receiver = positionContext.explicitReceiver
        val implicitReceivers = basicContext.originalKtFile.scopeContext(positionContext.nameExpression).implicitReceivers

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

context(KaSession)
private fun KotlinExpressionNameReferencePositionContext.allowsOnlyNamedArguments(): Boolean {
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