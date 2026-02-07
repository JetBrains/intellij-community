// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaApplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaInapplicableCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.findValueArgument
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2ActualDeclarationContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2CallableCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2CallableReferenceCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2ChainCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2ClassReferenceCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2ClassifierCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2DeclarationFromOverridableMembersContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2DeclarationFromUnresolvedNameContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2ImportDirectivePackageMembersCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2InfixCallableCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2KDocCallableCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2KDocParameterNameContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2KeywordCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2NamedArgumentCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2OperatorNameCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2PackageCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2SameAsFileClassifierNameCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2SuperEntryContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2SuperMemberCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2TrailingFunctionParameterNameCompletionContributorBase
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2TypeInstantiationContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2TypeParameterConstraintNameInWhereClauseCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2VariableOrParameterNameWithTypeCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.K2WhenWithSubjectConditionContributor
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinUnknownPositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal object Completions {

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
        K2TypeInstantiationContributor(),
    )

    /**
     * Returns a [K2CompletionRunnerResult] containing information about how many elements
     * were added to the [resultSet] and which [K2ChainCompletionContributor]s were registered.
     */
    fun <T : KotlinRawPositionContext> complete(
        parameters: KotlinFirCompletionParameters,
        positionContext: T,
        resultSet: CompletionResultSet,
    ): K2CompletionRunnerResult {
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
        return completionRunner.runCompletion(completionContext, sections)
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
