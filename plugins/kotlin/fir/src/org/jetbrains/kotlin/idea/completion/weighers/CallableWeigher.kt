/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtPossibleMemberSymbol
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.UserDataProperty

internal object CallableWeigher {
    const val WEIGHER_ID = "kotlin.callableWeigher"
    private var LookupElement.callableWeight by UserDataProperty(Key<CallableWeight>("KOTLIN_CALLABlE_WEIGHT"))

    fun KtAnalysisSession.addWeight(context: WeighingContext, lookupElement: LookupElement, symbol: KtSymbol, substitutor: KtSubstitutor) {
        if (symbol !is KtCallableSymbol) return
        if (symbol is KtSyntheticJavaPropertySymbol) {
            addWeight(context, lookupElement, symbol.javaGetterSymbol, substitutor)
            return
        }
        val overriddenSymbols = symbol.getDirectlyOverriddenSymbols()
        if (overriddenSymbols.isNotEmpty()) {
            val weights = overriddenSymbols
                .mapNotNull { callableWeightByReceiver(it, context, null, substitutor) }
                .takeUnless { it.isEmpty() }
                ?: symbol.getAllOverriddenSymbols().map { callableWeightBasic(context, it, substitutor) }

            lookupElement.callableWeight = weights.minByOrNull { it.kind }
            return
        }
        lookupElement.callableWeight = callableWeightBasic(context, symbol, substitutor)
    }

    private fun KtAnalysisSession.callableWeightBasic(
        context: WeighingContext,
        symbol: KtCallableSymbol,
        substitutor: KtSubstitutor
    ): CallableWeight {
        callableWeightByReceiver(symbol, context, CallableWeight.receiverCastRequired, substitutor)?.let { return it }
        return when (symbol.getContainingSymbol()) {
            null, is KtPackageSymbol, is KtClassifierSymbol -> CallableWeight.globalOrStatic
            else -> CallableWeight.local
        }
    }

    private fun KtAnalysisSession.callableWeightByReceiver(
        symbol: KtCallableSymbol,
        context: WeighingContext,
        weightForReceiverTypeMismatch: CallableWeight?,
        substitutor: KtSubstitutor
    ): CallableWeight? {
        val actualExplicitReceiverType = context.explicitReceiver?.getKtType()
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
                                     weightForReceiverTypeMismatch)
        }
        if (weightBasedOnExtensionReceiver == weightForReceiverTypeMismatch) return weightForReceiverTypeMismatch

        // In Fir, a local function takes its containing function's dispatch receiver as its dispatch receiver. But we don't consider a
        // local function as a class member. Hence, here we return null so that it's handled by other logic.
        if (symbol.callableIdIfNonLocal == null) return null

        val expectedDispatchReceiverType = (symbol as? KtPossibleMemberSymbol)?.getDispatchReceiverType()
        val weightBasedOnDispatchReceiver = expectedDispatchReceiverType?.let { receiverType ->
            callableWeightByReceiver(
                symbol,
                actualImplicitReceiverTypes + listOfNotNull(actualExplicitReceiverType),
                receiverType,
                weightForReceiverTypeMismatch
            )
        }
        if (weightBasedOnDispatchReceiver == weightForReceiverTypeMismatch) return weightForReceiverTypeMismatch
        return weightBasedOnExtensionReceiver ?: weightBasedOnDispatchReceiver
    }

    private fun KtAnalysisSession.callableWeightByReceiver(
        symbol: KtCallableSymbol,
        actualReceiverTypes: List<KtType>,
        expectedReceiverType: KtType,
        shortcutWeight: CallableWeight?,
    ): CallableWeight? {
        if (expectedReceiverType is KtFunctionType) return null
        var bestMatchIndex: Int? = null
        var bestMatchWeightKind: CallableWeightKind? = null

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
        if (bestMatchWeightKind == null) return shortcutWeight
        return CallableWeight(bestMatchWeightKind, bestMatchIndex)
    }

    private fun KtAnalysisSession.callableWeightKindByReceiverType(
        symbol: KtCallableSymbol,
        actualReceiverType: KtType,
        expectedReceiverType: KtType,
    ): CallableWeightKind? = when {
        actualReceiverType isEqualTo expectedReceiverType -> when {
            isExtensionCallOnTypeParameterReceiver(symbol) -> CallableWeightKind.TYPE_PARAMETER_EXTENSION
            symbol.isExtension -> CallableWeightKind.THIS_TYPE_EXTENSION
            else -> CallableWeightKind.THIS_CLASS_MEMBER
        }
        actualReceiverType isSubTypeOf expectedReceiverType -> when {
            symbol.isExtension -> CallableWeightKind.BASE_TYPE_EXTENSION
            else -> CallableWeightKind.BASE_CLASS_MEMBER
        }
        else -> null
    }

    private fun KtAnalysisSession.isExtensionCallOnTypeParameterReceiver(symbol: KtCallableSymbol): Boolean {
        val originalSymbol = symbol.originalOverriddenSymbol
        val receiverParameterType = originalSymbol?.receiverType as? KtTypeParameterType ?: return false
        val parameterTypeOwner = receiverParameterType.symbol.getContainingSymbol() ?: return false
        return parameterTypeOwner == originalSymbol
    }

    enum class CallableWeightKind {
        LOCAL, // local non_extension
        THIS_CLASS_MEMBER,
        BASE_CLASS_MEMBER,
        THIS_TYPE_EXTENSION,
        BASE_TYPE_EXTENSION,
        GLOBAL_OR_STATIC, // global non_extension
        TYPE_PARAMETER_EXTENSION,
        RECEIVER_CAST_REQUIRED
    }

    class CallableWeight(
        val kind: CallableWeightKind,
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
            val local = CallableWeight(CallableWeightKind.LOCAL, null)
            val globalOrStatic = CallableWeight(CallableWeightKind.GLOBAL_OR_STATIC, null)
            val receiverCastRequired = CallableWeight(CallableWeightKind.RECEIVER_CAST_REQUIRED, null)
        }
    }


    private enum class Weight1 {
        LOCAL,
        MEMBER_OR_EXTENSION,
        GLOBAL_OR_STATIC,
        TYPE_PARAMETER_EXTENSION,
        RECEIVER_CAST_REQUIRED
    }

    private enum class Weight2 {
        THIS_CLASS_MEMBER,
        BASE_CLASS_MEMBER,
        THIS_TYPE_EXTENSION,
        BASE_TYPE_EXTENSION,
        OTHER
    }

    private data class CompoundWeight(val weight1: Weight1, val receiverIndex: Int, val weight2: Weight2) : Comparable<CompoundWeight> {
        override fun compareTo(other: CompoundWeight): Int {
            return compareValuesBy(
                this,
                other,
                { it.weight1 },
                { it.receiverIndex },
                { it.weight2 },
            )
        }
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*>? {
            val weight = element.callableWeight ?: return null
            val w1 = when (weight.kind) {
                CallableWeightKind.LOCAL -> Weight1.LOCAL

                CallableWeightKind.THIS_CLASS_MEMBER,
                CallableWeightKind.BASE_CLASS_MEMBER,
                CallableWeightKind.THIS_TYPE_EXTENSION,
                CallableWeightKind.BASE_TYPE_EXTENSION -> Weight1.MEMBER_OR_EXTENSION

                CallableWeightKind.GLOBAL_OR_STATIC -> Weight1.GLOBAL_OR_STATIC

                CallableWeightKind.TYPE_PARAMETER_EXTENSION -> Weight1.TYPE_PARAMETER_EXTENSION

                CallableWeightKind.RECEIVER_CAST_REQUIRED -> Weight1.RECEIVER_CAST_REQUIRED
            }
            val w2 = when (weight.kind) {
                CallableWeightKind.THIS_CLASS_MEMBER -> Weight2.THIS_CLASS_MEMBER
                CallableWeightKind.BASE_CLASS_MEMBER -> Weight2.BASE_CLASS_MEMBER
                CallableWeightKind.THIS_TYPE_EXTENSION -> Weight2.THIS_TYPE_EXTENSION
                CallableWeightKind.BASE_TYPE_EXTENSION -> Weight2.BASE_TYPE_EXTENSION
                else -> Weight2.OTHER
            }
            return CompoundWeight(w1, weight.receiverIndex ?: Int.MAX_VALUE, w2)
        }
    }
}