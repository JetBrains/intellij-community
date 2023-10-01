// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
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
        GLOBAL_OR_STATIC, // global non_extension
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
    ): CallableMetadata? {
        val symbol = signature.symbol
        if (symbol is KtSyntheticJavaPropertySymbol) {
            return getCallableMetadata(context, symbol.javaGetterSymbol.asSignature(), symbolOrigin)
        }

        val scopeIndex = (symbolOrigin as? CompletionSymbolOrigin.Scope)?.kind?.indexInTower

        return when (signature.symbol.symbolKind) {
            KtSymbolKind.TOP_LEVEL,
            KtSymbolKind.CLASS_MEMBER -> {
                if (signature.symbol.isExtension) {
                    extensionWeight(signature, context)
                } else {
                    nonExtensionWeight(signature, context)
                }
            }

            KtSymbolKind.LOCAL -> CallableMetadata(CallableKind.LOCAL, scopeIndex)
            else -> null
        } ?: CallableMetadata(CallableKind.GLOBAL_OR_STATIC, scopeIndex)
    }

    context(KtAnalysisSession)
    private fun nonExtensionWeight(
        signature: KtCallableSignature<*>,
        context: WeighingContext,
    ): CallableMetadata? {
        val symbol = signature.symbol

        val expectedReceiver = signature.symbol.originalContainingClassForOverride ?: return null
        val expectedReceiverType = buildClassType(expectedReceiver)
        val actualReceiverTypes = getActualReceiverTypes(context)

        val replaceTypeArguments = expectedReceiverType is KtNonErrorClassType && expectedReceiverType.ownTypeArguments.isNotEmpty()
        // replace type arguments to correctly compare actual types with built expected type
        val correctedActualReceiverTypes = actualReceiverTypes.applyIf(replaceTypeArguments) {
            mapNotNull { it.replaceTypeArgumentsWithStarProjections() }
        }

        val hasOverriddenSymbols = symbol.isOverride ||
                symbol.getDirectlyOverriddenSymbols().isNotEmpty() ||
                symbol.getAllOverriddenSymbols().isNotEmpty()

        return callableWeightByReceiver(
            symbol,
            correctedActualReceiverTypes,
            expectedReceiverType,
        )
            // currently override members are considered as non-immediate in completion
            .applyIf(hasOverriddenSymbols) { CallableMetadata(kind.correspondingBaseForThisOrSelf, scopeIndex) }
    }

    context(KtAnalysisSession)
    private fun extensionWeight(
        signature: KtCallableSignature<*>,
        context: WeighingContext,
    ): CallableMetadata? {
        val actualReceiverTypes = getActualReceiverTypes(context)
        val expectedExtensionReceiverType = signature.receiverType ?: return null

        // If a symbol expects an extension receiver, then either
        //   * the call site explicitly specifies the extension receiver , or
        //   * the call site specifies no receiver.
        // In other words, in this case, an explicit receiver can never be a dispatch receiver.
        val weightBasedOnExtensionReceiver = callableWeightByReceiver(
            signature.symbol,
            actualReceiverTypes,
            expectedExtensionReceiverType,
        )
        return weightBasedOnExtensionReceiver
    }

    context(KtAnalysisSession)
    private fun getActualReceiverTypes(context: WeighingContext): List<KtType> {
        val actualExplicitReceiverType = context.explicitReceiver?.let {
            getReferencedClassTypeInCallableReferenceExpression(it)
                ?: getQualifierClassTypeInKDocName(it)
                ?: (it as? KtExpression)?.getKtType()
        }

        return if (actualExplicitReceiverType != null) {
            listOf(actualExplicitReceiverType)
        } else {
            context.implicitReceiver.map { it.type }
        }
    }

    context(KtAnalysisSession)
    private val KtCallableSymbol.isOverride: Boolean
        get() = when (this) {
            is KtFunctionSymbol -> isOverride
            is KtPropertySymbol -> isOverride
            else -> false
        }

    /**
     * Return the type from the referenced class if this explicit receiver is a receiver in a callable reference expression. For example,
     * in the following code, `String` is such a receiver. And this method should return the `String` type in this case.
     * ```
     * val l = String::length
     * ```
     */
    context(KtAnalysisSession)
    private fun getReferencedClassTypeInCallableReferenceExpression(explicitReceiver: KtElement): KtType? {
        val callableReferenceExpression = explicitReceiver.getParentOfType<KtCallableReferenceExpression>(strict = true) ?: return null
        if (callableReferenceExpression.lhs != explicitReceiver) return null
        val symbol = when (explicitReceiver) {
            is KtDotQualifiedExpression -> explicitReceiver.selectorExpression?.mainReference?.resolveToExpandedSymbol()
            is KtNameReferenceExpression -> explicitReceiver.mainReference.resolveToExpandedSymbol()
            else -> return null
        }
        if (symbol !is KtClassLikeSymbol) return null
        return buildClassType(symbol)
    }

    context(KtAnalysisSession)
    private fun getQualifierClassTypeInKDocName(explicitReceiver: KtElement): KtType? {
        if (explicitReceiver !is KDocName) return null

        val symbol = explicitReceiver.mainReference.resolveToSymbol() as? KtClassLikeSymbol ?: return null
        return buildClassType(symbol)
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
        actualReceiverTypes: List<KtType>,
        expectedReceiverType: KtType,
    ): CallableMetadata {
        var allReceiverTypesMatch = true
        var bestMatchIndex: Int? = null
        var bestMatchWeightKind: CallableKind? = null

        for ((i, actualReceiverType) in actualReceiverTypes.withIndex()) {
            val weightKind = callableWeightKindByReceiverType(symbol, actualReceiverType, expectedReceiverType)
            if (weightKind != null) {
                if (bestMatchWeightKind == null || weightKind < bestMatchWeightKind) {
                    bestMatchWeightKind = weightKind
                    bestMatchIndex = i
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
        if (allReceiverTypesMatch && actualReceiverTypes.size > 1) bestMatchIndex = null

        return CallableMetadata(bestMatchWeightKind, bestMatchIndex)
    }

    context(KtAnalysisSession)
    private fun callableWeightKindByReceiverType(
        symbol: KtCallableSymbol,
        actualReceiverType: KtType,
        expectedReceiverType: KtType,
    ): CallableKind? {
        val receiverTypes = when (actualReceiverType) {
            is KtIntersectionType -> actualReceiverType.conjuncts
            else -> listOf(actualReceiverType)
        }

        return when {
            receiverTypes.any { it isEqualTo expectedReceiverType } -> when {
                isExtensionCallOnTypeParameterReceiver(symbol) -> CallableKind.TYPE_PARAMETER_EXTENSION
                symbol.isExtension -> CallableKind.THIS_TYPE_EXTENSION
                else -> CallableKind.THIS_CLASS_MEMBER
            }

            receiverTypes.any { it isSubTypeOf expectedReceiverType } -> when {
                symbol.isExtension -> CallableKind.BASE_TYPE_EXTENSION
                else -> CallableKind.BASE_CLASS_MEMBER
            }

            else -> null
        }
    }

    context(KtAnalysisSession)
    private fun isExtensionCallOnTypeParameterReceiver(symbol: KtCallableSymbol): Boolean {
        val originalSymbol = symbol.unwrapFakeOverrides
        val receiverParameterType = originalSymbol.receiverType as? KtTypeParameterType ?: return false
        val parameterTypeOwner = receiverParameterType.symbol.getContainingSymbol() ?: return false
        return parameterTypeOwner == originalSymbol
    }
}