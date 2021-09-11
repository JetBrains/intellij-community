/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.analyse
import org.jetbrains.kotlin.idea.frontend.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.idea.frontend.api.types.KtClassErrorType
import org.jetbrains.kotlin.idea.frontend.api.types.KtClassType
import org.jetbrains.kotlin.idea.frontend.api.types.KtFlexibleType
import org.jetbrains.kotlin.idea.frontend.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.*

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
        val parent = callElement.parent
        val receiver = if (parent is KtDotQualifiedExpression && parent.selectorExpression == callElement) {
            parent.receiverExpression
        } else null
        val fileSymbol = callElement.containingKtFile.getFileSymbol()
        val symbols = reference.resolveToSymbols().filterIsInstance<KtSymbolWithTypeParameters>()
            .filter { filterCandidate(it, callElement, fileSymbol, receiver) }

        // Multiple overloads may have the same type parameters (see Overloads.kt test), so we select the distinct ones.
        return symbols.distinctBy { buildPresentation(fetchCandidateInfo(it), -1).first }
    }

    override fun getArgumentListAllowedParentClasses() = setOf(KtCallElement::class.java)
}

abstract class KotlinHighLevelTypeArgumentInfoHandlerBase : AbstractKotlinTypeArgumentInfoHandler() {
    protected abstract fun KtAnalysisSession.findParameterOwners(argumentList: KtTypeArgumentList): Collection<KtSymbolWithTypeParameters>?

    override fun fetchCandidateInfos(argumentList: KtTypeArgumentList): List<CandidateInfo>? {
        analyse(argumentList) {
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
            val renderedType = it.render(KtTypeRendererOptions.SHORT_NAMES)
            UpperBoundInfo(isNullableAnyOrFlexibleAny, renderedType)
        }
        return TypeParameterInfo(parameter.name.asString(), parameter.isReified, parameter.variance, upperBounds)
    }
}