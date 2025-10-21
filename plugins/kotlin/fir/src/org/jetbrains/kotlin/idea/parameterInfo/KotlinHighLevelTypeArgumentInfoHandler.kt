// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.components.createUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isAnyType
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassErrorType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.filterCandidateByReceiverTypeAndVisibility
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.types.Variance

/**
 * Presents type argument info for class type references (e.g., property type in declaration, base class in super types list).
 */
class KotlinHighLevelClassTypeArgumentInfoHandler : KotlinHighLevelTypeArgumentInfoHandlerBase() {
    context(_: KaSession)
    override fun findParameterOwners(argumentList: KtTypeArgumentList): Collection<KaDeclarationSymbol>? {
        val typeReference = argumentList.parentOfType<KtTypeReference>() ?: return null
        return when (val ktType = typeReference.type) {
            is KaClassType -> listOfNotNull(ktType.expandedSymbol as? KaNamedClassSymbol)
            is KaClassErrorType -> {
                ktType.candidateSymbols.mapNotNull { candidateSymbol ->
                    when (candidateSymbol) {
                        is KaClassSymbol -> candidateSymbol
                        is KaTypeAliasSymbol -> candidateSymbol.expandedType.expandedSymbol
                        else -> null
                    } as? KaNamedClassSymbol
                }
            }
            else -> return null
        }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtUserType::class.java)
}

/**
 * Presents type argument info for function calls (including constructor calls).
 */
class KotlinHighLevelFunctionTypeArgumentInfoHandler : KotlinHighLevelTypeArgumentInfoHandlerBase() {
    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun findParameterOwners(argumentList: KtTypeArgumentList): Collection<KaDeclarationSymbol>? {
        val callElement = argumentList.parentOfType<KtCallElement>() ?: return null
        // A call element may not be syntactically complete (e.g., missing parentheses: `foo<>`). In that case, `callElement.resolveCallOld()`
        // will NOT return a KaCall because there is no FirFunctionCall there. We find the symbols using the callee name instead.
        val reference = callElement.calleeExpression?.references?.singleOrNull() as? KtSimpleNameReference ?: return null
        val explicitReceiver = callElement.getQualifiedExpressionForSelector()?.receiverExpression
        val fileSymbol = callElement.containingKtFile.symbol

        val visibilityChecker = createUseSiteVisibilityChecker(fileSymbol, explicitReceiver, callElement)
        val symbols = callElement.resolveToCallCandidates()
            .mapNotNull { (it.candidate as? KaCallableMemberCall<*, *>)?.partiallyAppliedSymbol?.signature }
            .filterIsInstance<KaFunctionSignature<*>>()
            .filter { candidate ->
                // We use the `LENIENT` error type policy to permit candidates even when there are partially specified type arguments (e.g.,
                // `foo<>` for `foo<A, B>`), as the specified type arguments may directly affect a candidate's receiver type.
                //
                // ```
                // fun <T, K> List<T>.foo() {}
                //
                // listOf(1).foo<<caret>>()
                // ```
                //
                // In this example, the call candidate for `fun <T, K> List<T>.foo() {}` has the following receiver type: `List<ERROR>`,
                // because `T` is not specified. But the call still fits `foo`. The user just hasn't written down the type argument yet.
                filterCandidateByReceiverTypeAndVisibility(
                    candidate,
                    callElement,
                    explicitReceiver,
                    visibilityChecker,
                    KaSubtypingErrorTypePolicy.LENIENT,
                )
            }

        // Multiple overloads may have the same type parameters (see Overloads.kt test), so we select the distinct ones.
        return symbols.distinctBy { buildPresentation(fetchCandidateInfo(it.symbol), -1).first }.map { it.symbol }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtCallElement::class.java)
}

abstract class KotlinHighLevelTypeArgumentInfoHandlerBase : AbstractKotlinTypeArgumentInfoHandler() {
    context(_: KaSession)
    protected abstract fun findParameterOwners(argumentList: KtTypeArgumentList): Collection<KaDeclarationSymbol>?

    override fun fetchCandidateInfos(argumentList: KtTypeArgumentList): List<CandidateInfo>? {
        analyze(argumentList) {
            val parameterOwners = findParameterOwners(argumentList) ?: return null
            return parameterOwners.map { fetchCandidateInfo(it) }
        }
    }

    context(_: KaSession)
    protected fun fetchCandidateInfo(parameterOwner: KaDeclarationSymbol): CandidateInfo {
        @OptIn(KaExperimentalApi::class)
        return CandidateInfo(parameterOwner.typeParameters.map { fetchTypeParameterInfo(it) })
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun fetchTypeParameterInfo(parameter: KaTypeParameterSymbol): TypeParameterInfo {
        val upperBounds = parameter.upperBounds.map {
            val isNullableAnyOrFlexibleAny = if (it is KaFlexibleType) {
                it.lowerBound.isAnyType && !it.lowerBound.isMarkedNullable && it.upperBound.isAnyType && it.upperBound.isMarkedNullable
            } else {
                it.isAnyType && it.isMarkedNullable
            }
            val renderedType = it.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
            UpperBoundInfo(isNullableAnyOrFlexibleAny, renderedType)
        }
        return TypeParameterInfo(parameter.name.asString(), parameter.isReified, parameter.variance, upperBounds)
    }
}