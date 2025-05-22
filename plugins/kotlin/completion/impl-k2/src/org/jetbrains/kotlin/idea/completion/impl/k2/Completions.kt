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
                positionContext = positionContext,
                sink = LookupElementSink(resultSet, parameters),
            )
        } finally {
            after()
        }
    }

    context(KaSession)
    private fun complete(
        positionContext: KotlinRawPositionContext,
        sink: LookupElementSink,
    ) {
        val weighingContext = when (positionContext) {
            is KotlinNameReferencePositionContext -> WeighingContext.create(sink.parameters, positionContext)
            else -> WeighingContext.create(sink.parameters, elementInCompletionFile = positionContext.position)
        }

        when (positionContext) {
            is KotlinExpressionNameReferencePositionContext -> {
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
                K2ActualDeclarationContributor(sink, priority = 1)
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
