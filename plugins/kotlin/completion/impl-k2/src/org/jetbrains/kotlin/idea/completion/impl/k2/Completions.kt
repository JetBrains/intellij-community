// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.checkers.CompletionVisibilityChecker
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.*
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {

    context(KaSession)
    fun complete(
        basicContext: FirBasicCompletionContext,
        positionContext: KotlinRawPositionContext,
        sink: LookupElementSink,
    ) {
        val weighingContext = when (positionContext) {
            is KotlinNameReferencePositionContext -> WeighingContext.create(basicContext, positionContext)
            else -> WeighingContext.create(basicContext, elementInCompletionFile = positionContext.position)
        }
        val visibilityChecker = CompletionVisibilityChecker(basicContext, positionContext)

        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> {
                if (positionContext.allowsOnlyNamedArguments()) {
                    FirNamedArgumentCompletionContributor(visibilityChecker, sink)
                        .complete(positionContext, weighingContext)
                } else {
                    FirKeywordCompletionContributor(visibilityChecker, sink)
                        .complete(positionContext, weighingContext)
                    FirNamedArgumentCompletionContributor(visibilityChecker, sink)
                        .complete(positionContext, weighingContext)
                    FirCallableCompletionContributor(visibilityChecker, sink, withTrailingLambda = true)
                        .complete(positionContext, weighingContext)
                    FirClassifierCompletionContributor(visibilityChecker, sink)
                        .complete(positionContext, weighingContext)
                    FirPackageCompletionContributor(visibilityChecker, sink, priority = 1)
                        .complete(positionContext, weighingContext)
                }
            }

            is KotlinSuperReceiverNameReferencePositionContext -> {
                FirSuperMemberCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinTypeNameReferencePositionContext -> {
                val allowClassifiersAndPackagesForPossibleExtensionCallables =
                    !positionContext.hasNoExplicitReceiver()
                            || basicContext.parameters.invocationCount > 0
                            || sink.prefixMatcher.prefix.firstOrNull()?.isLowerCase() != true

                if (allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirClassifierCompletionContributor(visibilityChecker, sink)
                        .complete(positionContext, weighingContext)
                }
                FirKeywordCompletionContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                if (allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirPackageCompletionContributor(visibilityChecker, sink, priority = 2)
                        .complete(positionContext, weighingContext)
                }
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                K2ActualDeclarationContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinAnnotationTypeNameReferencePositionContext -> {
                FirAnnotationCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(visibilityChecker, sink, priority = 2)
                    .complete(positionContext, weighingContext)
            }

            is KotlinSuperTypeCallNameReferencePositionContext -> {
                FirSuperEntryContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinImportDirectivePositionContext -> {
                FirPackageCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirImportDirectivePackageMembersCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinPackageDirectivePositionContext -> {
                FirPackageCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinTypeConstraintNameInWhereClausePositionContext -> {
                FirTypeParameterConstraintNameInWhereClauseCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinMemberDeclarationExpectedPositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinLabelReferencePositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinUnknownPositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinClassifierNamePositionContext -> {
                FirSameAsFileClassifierNameCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }

            is KotlinWithSubjectEntryPositionContext -> {
                FirWhenWithSubjectConditionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirCallableCompletionContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }

            is KotlinCallableReferencePositionContext -> {
                FirClassReferenceCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirCallableReferenceCompletionContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirClassifierReferenceCompletionContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }

            is KotlinInfixCallPositionContext -> {
                FirKeywordCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirInfixCallableCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is KotlinSimpleParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KDocParameterNamePositionContext -> {
                FirKDocParameterNameContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
            }

            is KDocLinkNamePositionContext -> {
                FirKDocCallableCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirClassifierCompletionContributor(visibilityChecker, sink)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(visibilityChecker, sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }
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

private fun KotlinTypeNameReferencePositionContext.hasNoExplicitReceiver(): Boolean {
    val declaration = typeReference?.parent
        ?: return false

    return (declaration is KtNamedFunction || declaration is KtProperty)
            && explicitReceiver == null
}
