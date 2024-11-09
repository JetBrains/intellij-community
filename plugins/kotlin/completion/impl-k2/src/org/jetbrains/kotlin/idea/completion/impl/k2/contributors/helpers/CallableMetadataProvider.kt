// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors.helpers

import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.analysis.api.utils.resolveToExpandedSymbol
import org.jetbrains.kotlin.idea.completion.lookups.isExtensionCall
import org.jetbrains.kotlin.idea.completion.reference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
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

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getCallableMetadata(
        signature: KaCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
        actualReceiverTypes: List<List<KaType>>,
        isFunctionalVariableCall: Boolean,
    ): CallableMetadata? {
        val symbol = signature.symbol
        return if (symbol is KaSyntheticJavaPropertySymbol) {
            getCallableMetadata(
                signature = symbol.javaGetterSymbol.asSignature(),
                symbolOrigin = symbolOrigin,
                actualReceiverTypes = actualReceiverTypes,
                isFunctionalVariableCall = isFunctionalVariableCall,
            )
        } else if (symbol.isExtensionCall(isFunctionalVariableCall)) {
            extensionWeight(signature, actualReceiverTypes, isFunctionalVariableCall)
        } else when (val scopeKind = (symbolOrigin as? CompletionSymbolOrigin.Scope)?.kind) {
            is KaScopeKind.LocalScope -> CallableMetadata(CallableKind.LOCAL, scopeKind.indexInTower)

            is KaScopeKind.TypeScope,
            is KaScopeKind.StaticMemberScope -> nonExtensionWeight(signature, actualReceiverTypes)

            is KaScopeKind.TypeParameterScope -> null

            is KaScopeKind.ImportingScope,
            is KaScopeKind.PackageMemberScope,
            is KaScopeKind.ScriptMemberScope,
            null -> CallableMetadata(CallableKind.GLOBAL, scopeKind?.indexInTower)
        }
    }

    context(KaSession)
    private fun nonExtensionWeight(
        signature: KaCallableSignature<*>,
        flattenedActualReceiverTypes: List<List<KaType>>,
    ): CallableMetadata? {
        val symbol = signature.symbol

        val expectedReceiver = getExpectedNonExtensionReceiver(signature.symbol) ?: return null
        val expectedReceiverType = buildClassType(expectedReceiver)

        val replaceTypeArguments = expectedReceiverType is KaClassType && expectedReceiverType.typeArguments.isNotEmpty()
        val actualReceiverTypes = if (replaceTypeArguments) {
            // replace type arguments to correctly compare actual types with built expected type
            flattenedActualReceiverTypes.map { typeConjuncts ->
                typeConjuncts.mapNotNull { it.replaceTypeArgumentsWithStarProjections() }
            }
        } else flattenedActualReceiverTypes

        val hasOverriddenSymbols = symbol.isOverride ||
                symbol.directlyOverriddenSymbols.any() ||
                symbol.allOverriddenSymbols.any()

        return callableWeightByReceiver(
            symbol,
            actualReceiverTypes,
            expectedReceiverType,
        )
            // currently override members are considered as non-immediate in completion
            .applyIf(hasOverriddenSymbols) { CallableMetadata(kind.correspondingBaseForThisOrSelf, scopeIndex) }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun extensionWeight(
        signature: KaCallableSignature<*>,
        actualReceiverTypes: List<List<KaType>>,
        isFunctionalVariableCall: Boolean,
    ): CallableMetadata? {
        val expectedExtensionReceiverType = if (isFunctionalVariableCall) {
            (signature.returnType as? KaFunctionType)?.receiverType
        } else {
            // if extension has type parameters, `KtExtensionApplicabilityResult.substitutor` may contain captured types
            signature.receiverType?.approximateToSuperPublicDenotableOrSelf(approximateLocalTypes = false)
        } ?: return null

        // If a symbol expects an extension receiver, then either
        //   * the call site explicitly specifies the extension receiver , or
        //   * the call site specifies no receiver.
        // In other words, in this case, an explicit receiver can never be a dispatch receiver.
        return callableWeightByReceiver(
            signature.symbol,
            actualReceiverTypes,
            expectedExtensionReceiverType,
        )
    }

    context(KaSession)
    private fun getExpectedNonExtensionReceiver(symbol: KaCallableSymbol): KaClassSymbol? {
        val containingClass = symbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol
        return if (symbol is KaConstructorSymbol && (containingClass as? KaNamedClassSymbol)?.isInner == true) {
            containingClass.containingDeclaration as? KaClassSymbol
        } else {
            containingClass
        }
    }

    context(KaSession)
    private fun receiverTypes(
        receiver: KtElement,
    ): List<KaType>? {
        val expandedSymbol = receiver.reference()
            ?.resolveToExpandedSymbol()

        if (expandedSymbol is KaClassLikeSymbol
            && (isInCallableReferenceExpression(receiver)
                    || receiver is KDocName)
        ) {
            return listOfNotNull(
                expandedSymbol,
                expandedSymbol.companionObject,
            ).map { buildClassType(it) }
        }

        if (receiver is KtExpression) {
            val receiverType = receiver.getTypeWithCorrectedNullability(expandedSymbol)
            if (receiverType != null) return listOf(receiverType)
        }

        return null
    }

    // todo refactor; move to an appropriate place
    context(KaSession)
    fun calculateActualReceiverTypes(
        explicitReceiver: KtElement?,
        implicitReceivers: () -> List<KaImplicitReceiver>,
    ): List<List<KaType>> {
        val receiverTypes = explicitReceiver?.let {
            receiverTypes(it)
        } ?: implicitReceivers().map { it.type }

        return receiverTypes
            .filterNot { it is KaErrorType }
            .map { it.flatten() }
    }

    private inline val KaClassLikeSymbol.companionObject: KaNamedClassSymbol?
        get() = (this as? KaNamedClassSymbol)?.companionObject

    context(KaSession)
    private fun KaType.flatten(): List<KaType> = when (this) {
        is KaIntersectionType -> conjuncts.flatMap { it.flatten() }
        else -> listOf(this)
    }

    context(KaSession)
    private fun KtExpression.getTypeWithCorrectedNullability(
        referenceClass: KaSymbol? = null,
    ): KaType? {
        val expressionType: KaType? = expressionType?.takeUnless { it.isUnitType }
            ?: when (val symbol = referenceClass) {
                is KaTypeAliasSymbol -> symbol.expandedType
                is KaClassifierSymbol -> symbol.defaultType
                is KaCallableSymbol -> symbol.returnType
                else -> null
            }

        return expressionType?.applyIf(parent is KtSafeQualifiedExpression) {
            withNullability(KaTypeNullability.NON_NULLABLE)
        }
    }

    context(KaSession)
    private val KaCallableSymbol.isOverride: Boolean
        get() = when (this) {
            is KaNamedFunctionSymbol -> isOverride
            is KaPropertySymbol -> isOverride
            else -> false
        }

    /**
     * Checks whether this explicit receiver is a receiver in a callable reference expression. For example,
     * in the following code, `String` is such a receiver. And this method should return the `String` class in this case.
     * ```
     * val l = String::length
     * ```
     */
    context(KaSession)
    private fun isInCallableReferenceExpression(explicitReceiver: KtElement): Boolean =
        explicitReceiver.getParentOfType<KtCallableReferenceExpression>(strict = true)
            ?.lhs == explicitReceiver

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun buildClassType(symbol: KaClassLikeSymbol): KaType = buildClassType(symbol) {
        @OptIn(KaExperimentalApi::class)
        repeat(symbol.typeParameters.size) {
            argument(buildStarTypeProjection())
        }
    }

    context(KaSession)
    private fun KaType.replaceTypeArgumentsWithStarProjections(): KaType? =
        expandedSymbol?.let { buildClassType(it) }?.withNullability(nullability)

    context(KaSession)
    private fun callableWeightByReceiver(
        symbol: KaCallableSymbol,
        actualReceiverTypes: List<List<KaType>>,
        expectedReceiverType: KaType,
    ): CallableMetadata {
        var allReceiverTypesMatch = true
        var bestMatchIndex: Int? = null
        var bestMatchWeightKind: CallableKind? = null

        // minimal level corresponds to receivers with the closest scopes
        for ((level, actualReceiverTypeConjuncts) in actualReceiverTypes.withIndex()) {
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
        if (allReceiverTypesMatch && actualReceiverTypes.size > 1) bestMatchIndex = null

        return CallableMetadata(bestMatchWeightKind, bestMatchIndex)
    }

    context(KaSession)
    private fun callableWeightKindByReceiverType(
        symbol: KaCallableSymbol,
        actualReceiverType: KaType,
        expectedReceiverType: KaType,
    ): CallableKind? = when {
        actualReceiverType.semanticallyEquals(expectedReceiverType) -> when {
            isExtensionCallOnTypeParameterReceiver(symbol) -> CallableKind.TYPE_PARAMETER_EXTENSION
            symbol.isExtension -> CallableKind.THIS_TYPE_EXTENSION
            else -> CallableKind.THIS_CLASS_MEMBER
        }

        actualReceiverType.isSubtypeOf(expectedReceiverType) -> when {
            symbol.isExtension -> CallableKind.BASE_TYPE_EXTENSION
            else -> CallableKind.BASE_CLASS_MEMBER
        }

        else -> null
    }

    context(KaSession)
    private fun isExtensionCallOnTypeParameterReceiver(symbol: KaCallableSymbol): Boolean {
        val originalSymbol = symbol.fakeOverrideOriginal
        val receiverParameterType = originalSymbol.receiverType as? KaTypeParameterType ?: return false
        val parameterTypeOwner = receiverParameterType.symbol.containingDeclaration ?: return false
        return parameterTypeOwner == originalSymbol
    }
}