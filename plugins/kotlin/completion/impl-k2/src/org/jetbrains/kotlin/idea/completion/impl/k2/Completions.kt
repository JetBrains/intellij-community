// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
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
        val visibilityChecker = CompletionVisibilityChecker(basicContext, positionContext)

        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> {
                if (positionContext.allowsOnlyNamedArguments()) {
                    FirNamedArgumentCompletionContributor(visibilityChecker)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                } else {
                    FirKeywordCompletionContributor(visibilityChecker)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                    FirNamedArgumentCompletionContributor(visibilityChecker)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                    FirCallableCompletionContributor(visibilityChecker, withTrailingLambda = true)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                    FirClassifierCompletionContributor(visibilityChecker)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                    FirPackageCompletionContributor(visibilityChecker, priority = 1)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                }
            }

            is KotlinSuperReceiverNameReferencePositionContext -> {
                FirSuperMemberCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinTypeNameReferencePositionContext -> {
                if (visibilityChecker.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirClassifierCompletionContributor(visibilityChecker)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                }
                FirKeywordCompletionContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                if (visibilityChecker.allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirPackageCompletionContributor(visibilityChecker, priority = 2)
                        .completeWithPolicyController(policyController, positionContext, weighingContext)
                }
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                K2ActualDeclarationContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinAnnotationTypeNameReferencePositionContext -> {
                FirAnnotationCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirKeywordCompletionContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirPackageCompletionContributor(visibilityChecker, priority = 2)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinSuperTypeCallNameReferencePositionContext -> {
                FirSuperEntryContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinImportDirectivePositionContext -> {
                FirPackageCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirImportDirectivePackageMembersCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinPackageDirectivePositionContext -> {
                FirPackageCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinTypeConstraintNameInWhereClausePositionContext -> {
                FirTypeParameterConstraintNameInWhereClauseCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinMemberDeclarationExpectedPositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinLabelReferencePositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinUnknownPositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinClassifierNamePositionContext -> {
                FirSameAsFileClassifierNameCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinWithSubjectEntryPositionContext -> {
                FirWhenWithSubjectConditionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirCallableCompletionContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinCallableReferencePositionContext -> {
                FirClassReferenceCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirCallableReferenceCompletionContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirClassifierReferenceCompletionContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinInfixCallPositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirInfixCallableCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is KotlinSimpleParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirKeywordCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KotlinPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirKeywordCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KDocParameterNamePositionContext -> {
                FirKDocParameterNameContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }

            is KDocLinkNamePositionContext -> {
                FirKDocCallableCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirClassifierCompletionContributor(visibilityChecker)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
                FirPackageCompletionContributor(visibilityChecker, priority = 1)
                    .completeWithPolicyController(policyController, positionContext, weighingContext)
            }
        }
    }

    context(KaSession)
    private fun <C : KotlinRawPositionContext> FirCompletionContributor<C>.completeWithPolicyController(
        policyController: PolicyController,
        positionContext: C,
        weighingContext: WeighingContext,
    ) {
        val policy = AttributedElementsAddingPolicy(contributorClass = this@completeWithPolicyController.javaClass)
        policyController.invokeWithPolicy(policy) {
            complete(
                positionContext = positionContext,
                weighingContext = weighingContext,
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