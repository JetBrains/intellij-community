// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.analysis.api.types.*
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
    override fun KtAnalysisSession.findParameterOwners(argumentList: KtTypeArgumentList): Collection<KtSymbolWithTypeParameters>? {
        val typeReference = argumentList.parentOfType<KtTypeReference>() ?: return null
        val ktType = typeReference.getKtType() as? KtClassType ?: return null
        return when (ktType) {
            is KtNonErrorClassType -> listOfNotNull(ktType.expandedClassSymbol as? KtNamedClassOrObjectSymbol)
            is KtClassErrorType -> {
                ktType.candidateClassSymbols.mapNotNull { candidateSymbol ->
                    when (candidateSymbol) {
                        is KtClassOrObjectSymbol -> candidateSymbol
                        is KtTypeAliasSymbol -> candidateSymbol.expandedType.expandedClassSymbol
                        else -> null
                    } as? KtNamedClassOrObjectSymbol
                }
            }
        }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtUserType::class.java)
}

/**
 * Presents type argument info for function calls (including constructor calls).
 */
class KotlinHighLevelFunctionTypeArgumentInfoHandler : KotlinHighLevelTypeArgumentInfoHandlerBase() {
    override fun KtAnalysisSession.findParameterOwners(argumentList: KtTypeArgumentList): Collection<KtSymbolWithTypeParameters>? {
        val callElement = argumentList.parentOfType<KtCallElement>() ?: return null
        // A call element may not be syntactically complete (e.g., missing parentheses: `foo<>`). In that case, `callElement.resolveCall()`
        // will NOT return a KtCall because there is no FirFunctionCall there. We find the symbols using the callee name instead.
        val reference = callElement.calleeExpression?.references?.singleOrNull() as? KtSimpleNameReference ?: return null
        val explicitReceiver = callElement.getQualifiedExpressionForSelector()?.receiverExpression
        val fileSymbol = callElement.containingKtFile.getFileSymbol()
        val symbols = callElement.collectCallCandidates()
            .mapNotNull { (it.candidate as? KtCallableMemberCall<*, *>)?.partiallyAppliedSymbol?.signature }
            .filterIsInstance<KtFunctionLikeSignature<*>>()
            .filter { filterCandidateByReceiverTypeAndVisibility(it, callElement, fileSymbol, explicitReceiver) }

        // Multiple overloads may have the same type parameters (see Overloads.kt test), so we select the distinct ones.
        return symbols.distinctBy { buildPresentation(fetchCandidateInfo(it.symbol), -1).first }.map { it.symbol }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtCallElement::class.java)
}

abstract class KotlinHighLevelTypeArgumentInfoHandlerBase : AbstractKotlinTypeArgumentInfoHandler() {
    protected abstract fun KtAnalysisSession.findParameterOwners(argumentList: KtTypeArgumentList): Collection<KtSymbolWithTypeParameters>?

    override fun fetchCandidateInfos(argumentList: KtTypeArgumentList): List<CandidateInfo>? {
        analyze(argumentList) {
            val parameterOwners = findParameterOwners(argumentList) ?: return null
            return parameterOwners.map { fetchCandidateInfo(it) }
        }
    }

    protected fun KtAnalysisSession.fetchCandidateInfo(parameterOwner: KtSymbolWithTypeParameters): CandidateInfo {
        return CandidateInfo(parameterOwner.typeParameters.map { fetchTypeParameterInfo(it) })
    }

    private fun KtAnalysisSession.fetchTypeParameterInfo(parameter: KtTypeParameterSymbol): TypeParameterInfo {
        val upperBounds = parameter.upperBounds.map {
            val isNullableAnyOrFlexibleAny = if (it is KtFlexibleType) {
                it.lowerBound.isAny && !it.lowerBound.isMarkedNullable && it.upperBound.isAny && it.upperBound.isMarkedNullable
            } else {
                it.isAny && it.isMarkedNullable
            }
            val renderedType = it.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
            UpperBoundInfo(isNullableAnyOrFlexibleAny, renderedType)
        }
        return TypeParameterInfo(parameter.name.asString(), parameter.isReified, parameter.variance, upperBounds)
    }
}