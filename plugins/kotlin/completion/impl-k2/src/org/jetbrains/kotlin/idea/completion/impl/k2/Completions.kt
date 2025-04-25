// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionResultSet
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.*
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {

    fun complete(
        parameters: KotlinFirCompletionParameters,
        positionContext: KotlinRawPositionContext,
        resultSet: CompletionResultSet,
        before: KaSession.() -> Boolean = { true },
        after: KaSession.() -> Boolean = { true },
    ): Unit = analyze(parameters.completionFile) {
        try {
            if (!before()) return@analyze

            complete(
                parameters = parameters,
                positionContext = positionContext,
                sink = LookupElementSink(resultSet, parameters),
            )
        } finally {
            after()
        }
    }

    context(KaSession)
    private fun complete(
        parameters: KotlinFirCompletionParameters,
        positionContext: KotlinRawPositionContext,
        sink: LookupElementSink,
    ) {
        val weighingContext = when (positionContext) {
            is KotlinNameReferencePositionContext -> WeighingContext.create(parameters, positionContext)
            else -> WeighingContext.create(parameters, elementInCompletionFile = positionContext.position)
        }

        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> {
                FirTrailingFunctionParameterNameCompletionContributorBase.All(parameters, sink)
                    .complete(positionContext, weighingContext)
                if (positionContext.allowsOnlyNamedArguments()) {
                    FirNamedArgumentCompletionContributor(parameters, sink)
                        .complete(positionContext, weighingContext)
                } else {
                    FirKeywordCompletionContributor(parameters, sink)
                        .complete(positionContext, weighingContext)
                    FirNamedArgumentCompletionContributor(parameters, sink)
                        .complete(positionContext, weighingContext)
                    FirCallableCompletionContributor(parameters, sink, withTrailingLambda = true)
                        .complete(positionContext, weighingContext)
                    FirClassifierCompletionContributor(parameters, sink)
                        .complete(positionContext, weighingContext)
                    FirPackageCompletionContributor(parameters, sink, priority = 1)
                        .complete(positionContext, weighingContext)
                }
            }

            is KotlinSuperReceiverNameReferencePositionContext -> {
                FirSuperMemberCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinTypeNameReferencePositionContext -> {
                FirOperatorNameCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                val allowClassifiersAndPackagesForPossibleExtensionCallables =
                    !positionContext.hasNoExplicitReceiver()
                            || parameters.invocationCount > 0
                            || sink.prefixMatcher.prefix.firstOrNull()?.isLowerCase() != true

                if (allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirClassifierCompletionContributor(parameters, sink)
                        .complete(positionContext, weighingContext)
                }
                FirKeywordCompletionContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                if (allowClassifiersAndPackagesForPossibleExtensionCallables) {
                    FirPackageCompletionContributor(parameters, sink, priority = 2)
                        .complete(positionContext, weighingContext)
                }
                // For `val` and `fun` completion. For example, with `val i<caret>`, the fake file contains `val iX.f`. Hence a
                // FirTypeNameReferencePositionContext is created because `iX` is parsed as a type reference.
                FirDeclarationFromUnresolvedNameContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                K2ActualDeclarationContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinAnnotationTypeNameReferencePositionContext -> {
                FirAnnotationCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(parameters, sink, priority = 2)
                    .complete(positionContext, weighingContext)
            }

            is KotlinSuperTypeCallNameReferencePositionContext -> {
                FirSuperEntryContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinImportDirectivePositionContext -> {
                FirPackageCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirImportDirectivePackageMembersCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinPackageDirectivePositionContext -> {
                FirPackageCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinTypeConstraintNameInWhereClausePositionContext -> {
                FirTypeParameterConstraintNameInWhereClauseCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinMemberDeclarationExpectedPositionContext -> {
                FirKeywordCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinLabelReferencePositionContext -> {
                FirKeywordCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinUnknownPositionContext -> {
                FirKeywordCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinClassifierNamePositionContext -> {
                FirSameAsFileClassifierNameCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromUnresolvedNameContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }

            is KotlinWithSubjectEntryPositionContext -> {
                FirWhenWithSubjectConditionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirClassifierCompletionContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirCallableCompletionContributor(parameters, sink, priority = 2)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(parameters, sink, priority = 3)
                    .complete(positionContext, weighingContext)
            }

            is KotlinCallableReferencePositionContext -> {
                FirClassReferenceCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirCallableReferenceCompletionContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
                FirClassifierReferenceCompletionContributor(parameters, sink, priority = 1)
                    .complete(positionContext, weighingContext)
            }

            is KotlinInfixCallPositionContext -> {
                FirKeywordCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirInfixCallableCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinOperatorCallPositionContext,
            is KotlinIncorrectPositionContext -> {
                // do nothing, completion is not supposed to be called here
            }

            is KotlinSimpleParameterPositionContext -> {
                FirTrailingFunctionParameterNameCompletionContributorBase.Missing(parameters, sink)
                    .complete(positionContext, weighingContext)
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KotlinPrimaryConstructorParameterPositionContext -> {
                // for parameter declaration
                FirDeclarationFromUnresolvedNameContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirDeclarationFromOverridableMembersContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirKeywordCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirVariableOrParameterNameWithTypeCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KDocParameterNamePositionContext -> {
                FirKDocParameterNameContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
            }

            is KDocLinkNamePositionContext -> {
                FirKDocParameterNameContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirKDocCallableCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirClassifierCompletionContributor(parameters, sink)
                    .complete(positionContext, weighingContext)
                FirPackageCompletionContributor(parameters, sink, priority = 1)
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
