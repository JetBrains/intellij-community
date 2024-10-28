// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.*
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.AttributedElementsAddingPolicy
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {

    context(KaSession)
    fun complete(
        basicContext: FirBasicCompletionContext,
        positionContext: KotlinRawPositionContext,
        policyController: PolicyController,
    ) {
        val weighingContext = when (positionContext) {
            is KotlinNameReferencePositionContext -> WeighingContext.create(basicContext, positionContext)
            else -> WeighingContext.create(basicContext, elementInCompletionFile = positionContext.position)
        }
        val sessionParameters = FirCompletionSessionParameters(basicContext, positionContext)

        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> if (positionContext.allowsOnlyNamedArguments()) {
                FirNamedArgumentCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            } else {
                FirKeywordCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirNamedArgumentCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirCallableCompletionContributor(basicContext, withTrailingLambda = true)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirClassifierCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirPackageCompletionContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinSuperReceiverNameReferencePositionContext -> {
                FirSuperMemberCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinTypeNameReferencePositionContext -> {
                if (sessionParameters.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirClassifierCompletionContributor(basicContext)
                        .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                }
                FirKeywordCompletionContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                if (sessionParameters.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirPackageCompletionContributor(basicContext, priority = 2)
                        .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                }
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                FirDeclarationFromUnresolvedNameContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirDeclarationFromOverridableMembersContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                K2ActualDeclarationContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirVariableOrParameterNameWithTypeCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinAnnotationTypeNameReferencePositionContext -> {
                FirAnnotationCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirKeywordCompletionContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirPackageCompletionContributor(basicContext, priority = 2)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinSuperTypeCallNameReferencePositionContext -> {
                FirSuperEntryContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinImportDirectivePositionContext -> {
                FirPackageCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirImportDirectivePackageMembersCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinPackageDirectivePositionContext -> {
                FirPackageCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinTypeConstraintNameInWhereClausePositionContext -> {
                FirTypeParameterConstraintNameInWhereClauseCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinMemberDeclarationExpectedPositionContext -> {
                FirKeywordCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinLabelReferencePositionContext -> {
                FirKeywordCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinUnknownPositionContext -> {
                FirKeywordCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinClassifierNamePositionContext -> {
                FirSameAsFileClassifierNameCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirDeclarationFromUnresolvedNameContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinWithSubjectEntryPositionContext -> {
                FirWhenWithSubjectConditionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirCallableCompletionContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinCallableReferencePositionContext -> {
                FirClassReferenceCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirCallableReferenceCompletionContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirClassifierReferenceCompletionContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinInfixCallPositionContext -> {
                FirKeywordCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirInfixCallableCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is KotlinSimpleParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirKeywordCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirVariableOrParameterNameWithTypeCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KotlinPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirDeclarationFromOverridableMembersContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirKeywordCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirVariableOrParameterNameWithTypeCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KDocParameterNamePositionContext -> {
                FirKDocParameterNameContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }

            is KDocLinkNamePositionContext -> {
                FirKDocCallableCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirClassifierCompletionContributor(basicContext)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
                FirPackageCompletionContributor(basicContext, priority = 1)
                    .completeWithPolicyController(policyController, weighingContext, sessionParameters)
            }
        }
    }

    context(KaSession)
    private fun <C : KotlinRawPositionContext> FirCompletionContributor<C>.completeWithPolicyController(
        policyController: PolicyController,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val policy = AttributedElementsAddingPolicy(contributorClass = this@completeWithPolicyController.javaClass)
        policyController.invokeWithPolicy(policy) {
            complete(
                positionContext = @Suppress("UNCHECKED_CAST") (sessionParameters.positionContext as C), // TODO address dirty cast
                weighingContext = weighingContext,
                sessionParameters = sessionParameters,
            )
        }
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