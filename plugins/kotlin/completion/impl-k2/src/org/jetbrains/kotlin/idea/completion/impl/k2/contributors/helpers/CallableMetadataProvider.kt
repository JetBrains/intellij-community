// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.components.KtScopeKind
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
import org.jetbrains.kotlin.idea.completion.lookups.isExtensionCall
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object CallableMetadataProvider {

    class CallableMetadata(
        val kind: CallableKind,
        /**
         * In case of the local callable, the index of local scope in scope tower.
         * In case of the global or static imported callable, the index of non-local scope in scope tower.
         *
         * Otherwise, the index of the matched receiver.
         *
         * This number makes completion prefer candidates that are available from the innermost receiver
         * when all other things are equal. Explicit receiver is pushed to the end because if explicit receiver does not match, the entry
         * would not have showed up in the first place.
         *
         * For example, consider the code below
         *
         * ```
         * class Foo { // receiver 2
         *   fun String.f1() { // receiver 1
         *     fun Int.f2() { // receiver 0
         *       length // receiver index = 1
         *       listOf("").size // receiver index = 3 (explicit receiver is added to the end)
         *       "".f1() // receiver index = 3 (explicit receiver is honored over implicit (dispatch) receiver)
         *     }
         *   }
         * }
         * ```
         */
        val scopeIndex: Int?
    )

    /**
     * Note that [CallableKind] is used to sort completion suggestions, so the order of the enum entries should be changed with care
     */
    enum class CallableKind {
        LOCAL, // local non_extension
        THIS_CLASS_MEMBER,
        BASE_CLASS_MEMBER,
        THIS_TYPE_EXTENSION,
        BASE_TYPE_EXTENSION,
        GLOBAL, // global non_extension
        TYPE_PARAMETER_EXTENSION,
        RECEIVER_CAST_REQUIRED,
        ;
    }

    private val CallableKind.correspondingBaseForThisOrSelf: CallableKind
        get() = when (this) {
            CallableKind.THIS_CLASS_MEMBER -> CallableKind.BASE_CLASS_MEMBER
            CallableKind.THIS_TYPE_EXTENSION -> CallableKind.BASE_TYPE_EXTENSION
            else -> this
        }

    context(KtAnalysisSession)
    fun getCallableMetadata(
        context: WeighingContext,
        signature: KtCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        isFunctionalVariableCall: Boolean,
    ): CallableMetadata? {
        val symbol = signature.symbol
        if (symbol is KtSyntheticJavaPropertySymbol) {
            return getCallableMetadata(context, symbol.javaGetterSymbol.asSignature(), symbolOrigin, isFunctionalVariableCall)
        }

        if (symbol.isExtensionCall(isFunctionalVariableCall)) return extensionWeight(signature, context, isFunctionalVariableCall)

        return when (val scopeKind = (symbolOrigin as? CompletionSymbolOrigin.Scope)?.kind) {
            is KtScopeKind.LocalScope -> CallableMetadata(CallableKind.LOCAL, scopeKind.indexInTower)

            is KtScopeKind.TypeScope,
            is KtScopeKind.StaticMemberScope -> nonExtensionWeight(signature, context)

            is KtScopeKind.TypeParameterScope -> null

            is KtScopeKind.ImportingScope,
            is KtScopeKind.PackageMemberScope,
            is KtScopeKind.ScriptMemberScope,
            null -> CallableMetadata(CallableKind.GLOBAL, scopeKind?.indexInTower)
        }
    }

    context(KtAnalysisSession)
    private fun nonExtensionWeight(
        signature: KtCallableSignature<*>,
        context: WeighingContext,
    ): CallableMetadata? {
        val symbol = signature.symbol

        val expectedReceiver = signature.symbol.originalContainingClassForOverride ?: return null
        val expectedReceiverType = buildClassType(expectedReceiver)
        val flattenedActualReceiverTypes = getFlattenedActualReceiverTypes(context)

        val replaceTypeArguments = expectedReceiverType is KtNonErrorClassType && expectedReceiverType.ownTypeArguments.isNotEmpty()
        val correctedFlattenedActualReceiverTypes = if (replaceTypeArguments) {
            // replace type arguments to correctly compare actual types with built expected type
            flattenedActualReceiverTypes.map { typeConjuncts ->
                typeConjuncts.mapNotNull { it.replaceTypeArgumentsWithStarProjections() }
            }
        } else flattenedActualReceiverTypes

        val hasOverriddenSymbols = symbol.isOverride ||
                symbol.getDirectlyOverriddenSymbols().isNotEmpty() ||
                symbol.getAllOverriddenSymbols().isNotEmpty()

        return callableWeightByReceiver(
            symbol,
            correctedFlattenedActualReceiverTypes,
            expectedReceiverType,
        )
            // currently override members are considered as non-immediate in completion
            .applyIf(hasOverriddenSymbols) { CallableMetadata(kind.correspondingBaseForThisOrSelf, scopeIndex) }
    }

    context(KtAnalysisSession)
    private fun extensionWeight(
        signature: KtCallableSignature<*>,
        context: WeighingContext,
        isFunctionalVariableCall: Boolean,
    ): CallableMetadata? {
        val flattenedActualReceiverTypes = getFlattenedActualReceiverTypes(context)
        val expectedExtensionReceiverType = if (isFunctionalVariableCall) {
            (signature.returnType as? KtFunctionalType)?.receiverType
        } else {
            // if extension has type parameters, `KtExtensionApplicabilityResult.substitutor` may contain captured types
            signature.receiverType?.approximateToSuperPublicDenotableOrSelf(approximateLocalTypes = false)
        } ?: return null

        // If a symbol expects an extension receiver, then either
        //   * the call site explicitly specifies the extension receiver , or
        //   * the call site specifies no receiver.
        // In other words, in this case, an explicit receiver can never be a dispatch receiver.
        val weightBasedOnExtensionReceiver = callableWeightByReceiver(
            signature.symbol,
            flattenedActualReceiverTypes,
            expectedExtensionReceiverType,
        )
        return weightBasedOnExtensionReceiver
    }

    context(KtAnalysisSession)
    private fun getFlattenedActualReceiverTypes(context: WeighingContext): List<List<KtType>> {
        val actualExplicitReceiverTypes = context.explicitReceiver?.let { receiver ->
            val referencedClass = getReferencedClassInCallableReferenceExpression(receiver) ?: getQualifierClassInKDocName(receiver)
            val typesFromClass = referencedClass?.let { listOfNotNull(it, it.companionObject).map { buildClassType(it) } }

            typesFromClass ?: (receiver as? KtExpression)?.getTypeWithCorrectedNullability()?.let { listOf(it) }
        }

        val actualImplicitReceiverTypes = context.implicitReceiver.map { it.type }
        return (actualExplicitReceiverTypes ?: actualImplicitReceiverTypes)
            .filterNot { it is KtErrorType }
            .map { it.flatten() }
    }

    private val KtClassLikeSymbol.companionObject: KtNamedClassOrObjectSymbol?
        get() = (this as? KtNamedClassOrObjectSymbol)?.companionObject

    context(KtAnalysisSession)
    private fun KtType.flatten(): List<KtType> = when (this) {
        is KtIntersectionType -> conjuncts.flatMap { it.flatten() }
        else -> listOf(this)
    }

    context(KtAnalysisSession)
    private fun KtExpression.getTypeWithCorrectedNullability(): KtType? {
        val isSafeCall = parent is KtSafeQualifiedExpression
        return getKtType()?.applyIf(isSafeCall) { withNullability(KtTypeNullability.NON_NULLABLE) }
    }

    context(KtAnalysisSession)
    private val KtCallableSymbol.isOverride: Boolean
        get() = when (this) {
            is KtFunctionSymbol -> isOverride
            is KtPropertySymbol -> isOverride
            else -> false
        }

    /**
     * Returns referenced class if this explicit receiver is a receiver in a callable reference expression. For example,
     * in the following code, `String` is such a receiver. And this method should return the `String` class in this case.
     * ```
     * val l = String::length
     * ```
     */
    context(KtAnalysisSession)
    private fun getReferencedClassInCallableReferenceExpression(explicitReceiver: KtElement): KtClassLikeSymbol? {
        val callableReferenceExpression = explicitReceiver.getParentOfType<KtCallableReferenceExpression>(strict = true) ?: return null
        if (callableReferenceExpression.lhs != explicitReceiver) return null
        return explicitReceiver.reference()?.resolveToExpandedSymbol() as? KtClassLikeSymbol
    }

    context(KtAnalysisSession)
    private fun getQualifierClassInKDocName(explicitReceiver: KtElement): KtClassLikeSymbol? {
        if (explicitReceiver !is KDocName) return null

        return explicitReceiver.mainReference.resolveToSymbol() as? KtClassLikeSymbol
    }

    context(KtAnalysisSession)
    private fun buildClassType(symbol: KtClassLikeSymbol): KtClassType = buildClassType(symbol) {
        repeat(symbol.typeParameters.size) {
            argument(KtStarTypeProjection(token))
        }
    }

    context(KtAnalysisSession)
    private fun KtType.replaceTypeArgumentsWithStarProjections(): KtType? =
        expandedClassSymbol?.let { buildClassType(it) }?.withNullability(nullability)

    context(KtAnalysisSession)
    private fun callableWeightByReceiver(
        symbol: KtCallableSymbol,
        flattenedActualReceiverTypes: List<List<KtType>>,
        expectedReceiverType: KtType,
    ): CallableMetadata {
        var allReceiverTypesMatch = true
        var bestMatchIndex: Int? = null
        var bestMatchWeightKind: CallableKind? = null

        // minimal level corresponds to receivers with the closest scopes
        for ((level, actualReceiverTypeConjuncts) in flattenedActualReceiverTypes.withIndex()) {
            val weightKindsByMatchingReceiversFromLevel = actualReceiverTypeConjuncts
                .mapNotNull { callableWeightKindByReceiverType(symbol, it, expectedReceiverType) }

            val bestMatchWeightKindFromLevel = weightKindsByMatchingReceiversFromLevel.minOrNull()

            if (bestMatchWeightKindFromLevel != null) {
                if (bestMatchWeightKind == null || bestMatchWeightKindFromLevel < bestMatchWeightKind) {
                    bestMatchWeightKind = bestMatchWeightKindFromLevel
                    bestMatchIndex = level
                }
            } else {
                allReceiverTypesMatch = false
            }
        }

        if (bestMatchWeightKind == null) {
            return CallableMetadata(CallableKind.RECEIVER_CAST_REQUIRED, scopeIndex = null)
        }

        // use `null` for the receiver index if the symbol matches every actual receiver in order to prevent members of common super
        // classes such as `Any` from appearing on top
        if (allReceiverTypesMatch && flattenedActualReceiverTypes.size > 1) bestMatchIndex = null

        return CallableMetadata(bestMatchWeightKind, bestMatchIndex)
    }

    context(KtAnalysisSession)
    private fun callableWeightKindByReceiverType(
        symbol: KtCallableSymbol,
        actualReceiverType: KtType,
        expectedReceiverType: KtType,
    ): CallableKind? = when {
        actualReceiverType isEqualTo expectedReceiverType -> when {
            isExtensionCallOnTypeParameterReceiver(symbol) -> CallableKind.TYPE_PARAMETER_EXTENSION
            symbol.isExtension -> CallableKind.THIS_TYPE_EXTENSION
            else -> CallableKind.THIS_CLASS_MEMBER
        }

        actualReceiverType isSubTypeOf expectedReceiverType -> when {
            symbol.isExtension -> CallableKind.BASE_TYPE_EXTENSION
            else -> CallableKind.BASE_CLASS_MEMBER
        }

        else -> null
    }

    context(KtAnalysisSession)
    private fun isExtensionCallOnTypeParameterReceiver(symbol: KtCallableSymbol): Boolean {
        val originalSymbol = symbol.unwrapFakeOverrides
        val receiverParameterType = originalSymbol.receiverType as? KtTypeParameterType ?: return false
        val parameterTypeOwner = receiverParameterType.symbol.getContainingSymbol() ?: return false
        return parameterTypeOwner == originalSymbol
    }
}