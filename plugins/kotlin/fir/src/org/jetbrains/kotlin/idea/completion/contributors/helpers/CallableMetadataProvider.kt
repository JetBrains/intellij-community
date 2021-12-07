// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarProjectionTypeArgument
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object CallableMetadataProvider {

    class CallableMetadata(
        val kind: CallableKind,
        /**
         * The index of the matched receiver. This number makes completion prefer candidates that are available from the innermost receiver
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
        val receiverIndex: Int?
    ) {
        companion object {
            val local = CallableMetadata(CallableKind.Local, null)
            val globalOrStatic = CallableMetadata(CallableKind.GlobalOrStatic, null)
        }
    }

    sealed class CallableKind(private val index: Int) : Comparable<CallableKind> {
        object Local : CallableKind(0) // local non_extension
        object ThisClassMember : CallableKind(1)
        object BaseClassMember : CallableKind(2)
        object ThisTypeExtension : CallableKind(3)
        object BaseTypeExtension : CallableKind(4)
        object GlobalOrStatic : CallableKind(5) // global non_extension
        object TypeParameterExtension : CallableKind(6)
        class ReceiverCastRequired(val fullyQualifiedCastType: String) : CallableKind(7)

        override fun compareTo(other: CallableKind): Int = this.index - other.index
    }

    fun KtAnalysisSession.getCallableMetadata(
        context: WeighingContext,
        symbol: KtSymbol,
        substitutor: KtSubstitutor
    ): CallableMetadata? {
        if (symbol !is KtCallableSymbol) return null
        if (symbol is KtSyntheticJavaPropertySymbol) {
            return getCallableMetadata(context, symbol.javaGetterSymbol, substitutor)
        }
        val overriddenSymbols = symbol.getDirectlyOverriddenSymbols()
        if (overriddenSymbols.isNotEmpty()) {
            val weights = overriddenSymbols
                .mapNotNull { callableWeightByReceiver(it, context, substitutor, returnCastRequiredOnReceiverMismatch = false) }
                .takeUnless { it.isEmpty() }
                ?: symbol.getAllOverriddenSymbols().map { callableWeightBasic(context, it, substitutor) }

            return weights.minByOrNull { it.kind }
        }
        return callableWeightBasic(context, symbol, substitutor)
    }

    private fun KtAnalysisSession.callableWeightBasic(
        context: WeighingContext,
        symbol: KtCallableSymbol,
        substitutor: KtSubstitutor
    ): CallableMetadata {
        callableWeightByReceiver(symbol, context, substitutor, returnCastRequiredOnReceiverMismatch = true)?.let { return it }
        return when (symbol.getContainingSymbol()) {
            null, is KtPackageSymbol, is KtClassifierSymbol -> CallableMetadata.globalOrStatic
            else -> CallableMetadata.local
        }
    }

    private fun KtAnalysisSession.callableWeightByReceiver(
        symbol: KtCallableSymbol,
        context: WeighingContext,
        substitutor: KtSubstitutor,
        returnCastRequiredOnReceiverMismatch: Boolean
    ): CallableMetadata? {
        val actualExplicitReceiverType = context.explicitReceiver?.let {
            getReferencedClassTypeInCallableReferenceExpression(it) ?: it.getKtType()
        }
        val actualImplicitReceiverTypes = context.implicitReceiver.map { it.type }

        val expectedExtensionReceiverType = symbol.receiverType?.let { substitutor.substituteOrSelf(it) }
        val weightBasedOnExtensionReceiver = expectedExtensionReceiverType?.let { receiverType ->
            // If a symbol expects an extension receiver, then either
            //   * the call site explicitly specifies the extension receiver , or
            //   * the call site specifies no receiver.
            // In other words, in this case, an explicit receiver can never be a dispatch receiver.
            callableWeightByReceiver(symbol,
                                     actualExplicitReceiverType?.let { listOf(it) } ?: actualImplicitReceiverTypes,
                                     receiverType,
                                     returnCastRequiredOnReceiverMismatch
            )
        }
        if (returnCastRequiredOnReceiverMismatch && weightBasedOnExtensionReceiver?.kind is CallableKind.ReceiverCastRequired) return weightBasedOnExtensionReceiver

        // In Fir, a local function takes its containing function's dispatch receiver as its dispatch receiver. But we don't consider a
        // local function as a class member. Hence, here we return null so that it's handled by other logic.
        if (symbol.callableIdIfNonLocal == null) return null

        val expectedDispatchReceiverType = (symbol as? KtCallableSymbol)?.getDispatchReceiverType()
        val weightBasedOnDispatchReceiver = expectedDispatchReceiverType?.let { receiverType ->
            callableWeightByReceiver(
                symbol,
                actualImplicitReceiverTypes + listOfNotNull(actualExplicitReceiverType),
                receiverType,
                returnCastRequiredOnReceiverMismatch
            )
        }
        if (returnCastRequiredOnReceiverMismatch && weightBasedOnDispatchReceiver?.kind is CallableKind.ReceiverCastRequired) return weightBasedOnDispatchReceiver
        return weightBasedOnExtensionReceiver ?: weightBasedOnDispatchReceiver
    }

    /**
     * Return the type from the referenced class if this explicit receiver is a receiver in a callable reference expression. For example,
     * in the following code, `String` is such a receiver. And this method should return the `String` type in this case.
     * ```
     * val l = String::length
     * ```
     */
    private fun KtAnalysisSession.getReferencedClassTypeInCallableReferenceExpression(explicitReceiver: KtExpression): KtType? {
        val callableReferenceExpression = explicitReceiver.getParentOfType<KtCallableReferenceExpression>(strict = true) ?: return null
        if (callableReferenceExpression.lhs != explicitReceiver) return null
        val symbol = when (explicitReceiver) {
            is KtDotQualifiedExpression -> explicitReceiver.selectorExpression?.mainReference?.resolveToSymbol()
            is KtNameReferenceExpression -> explicitReceiver.mainReference.resolveToSymbol()
            else -> return null
        }
        if (symbol !is KtClassLikeSymbol) return null
        return buildClassType(symbol) {
            if (symbol is KtSymbolWithTypeParameters) {
                repeat(symbol.typeParameters.size) {
                    argument(KtStarProjectionTypeArgument(token))
                }
            }
        }
    }

    private fun KtAnalysisSession.callableWeightByReceiver(
        symbol: KtCallableSymbol,
        actualReceiverTypes: List<KtType>,
        expectedReceiverType: KtType,
        returnCastRequiredOnReceiverTypeMismatch: Boolean
    ): CallableMetadata? {
        if (expectedReceiverType is KtFunctionType) return null
        var bestMatchIndex: Int? = null
        var bestMatchWeightKind: CallableKind? = null

        for ((i, actualReceiverType) in actualReceiverTypes.withIndex()) {
            val weightKind = callableWeightKindByReceiverType(symbol, actualReceiverType, expectedReceiverType)
            if (weightKind != null) {
                if (bestMatchWeightKind == null || weightKind < bestMatchWeightKind) {
                    bestMatchWeightKind = weightKind
                    bestMatchIndex = i
                }
            }
        }

        // TODO: FE1.0 has logic that uses `null` for receiverIndex if the symbol matches every actual receiver in order to "prevent members
        //  of `Any` to show up on top". But that seems hacky and can cause collateral damage if the implicit receivers happen to implement
        //  some common interface. So that logic is left out here for now. We can add it back in future if needed.
        if (bestMatchWeightKind == null) {
            return if (returnCastRequiredOnReceiverTypeMismatch)
                CallableMetadata(CallableKind.ReceiverCastRequired(expectedReceiverType.render()), null)
            else null
        }
        return CallableMetadata(bestMatchWeightKind, bestMatchIndex)
    }

    private fun KtAnalysisSession.callableWeightKindByReceiverType(
        symbol: KtCallableSymbol,
        actualReceiverType: KtType,
        expectedReceiverType: KtType,
    ): CallableKind? = when {
        actualReceiverType isEqualTo expectedReceiverType -> when {
            isExtensionCallOnTypeParameterReceiver(symbol) -> CallableKind.TypeParameterExtension
            symbol.isExtension -> CallableKind.ThisTypeExtension
            else -> CallableKind.ThisClassMember
        }
        actualReceiverType isSubTypeOf expectedReceiverType -> when {
            symbol.isExtension -> CallableKind.BaseTypeExtension
            else -> CallableKind.BaseClassMember
        }
        else -> null
    }

    private fun KtAnalysisSession.isExtensionCallOnTypeParameterReceiver(symbol: KtCallableSymbol): Boolean {
        val originalSymbol = symbol.originalOverriddenSymbol
        val receiverParameterType = originalSymbol?.receiverType as? KtTypeParameterType ?: return false
        val parameterTypeOwner = receiverParameterType.symbol.getContainingSymbol() ?: return false
        return parameterTypeOwner == originalSymbol
    }
}