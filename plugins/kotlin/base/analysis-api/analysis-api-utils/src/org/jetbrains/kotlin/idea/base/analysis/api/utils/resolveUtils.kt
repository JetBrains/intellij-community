// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.components.KaUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.ArrayFqNames

// Analogous to Call.resolveCandidates() in plugins/kotlin/core/src/org/jetbrains/kotlin/idea/core/Utils.kt
context(KaSession)
@OptIn(KaExperimentalApi::class)
fun collectCallCandidates(callElement: KtElement): List<KaCallCandidateInfo> {
    val (candidates, explicitReceiver) = when (callElement) {
        is KtCallElement -> {
            val explicitReceiver = callElement.getQualifiedExpressionForSelector()?.receiverExpression
            callElement.resolveToCallCandidates() to explicitReceiver
        }

        is KtArrayAccessExpression -> callElement.resolveToCallCandidates() to callElement.arrayExpression
        else -> return emptyList()
    }

    if (candidates.isEmpty()) return emptyList()

    val visibilityChecker = createUseSiteVisibilityChecker(callElement.containingKtFile.symbol, explicitReceiver, callElement)
    return candidates.filter { filterCandidate(it, callElement, explicitReceiver, visibilityChecker) }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun filterCandidate(
    candidateInfo: KaCallCandidateInfo,
    callElement: KtElement,
    explicitReceiver: KtExpression?,
    visibilityChecker: KaUseSiteVisibilityChecker,
): Boolean {
    val candidateCall = candidateInfo.candidate
    if (candidateCall !is KaFunctionCall<*>) return false
    val signature = candidateCall.partiallyAppliedSymbol.signature
    return filterCandidateByReceiverTypeAndVisibility(signature, callElement, explicitReceiver, visibilityChecker)
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
fun filterCandidateByReceiverTypeAndVisibility(
    signature: KaFunctionSignature<KaFunctionSymbol>,
    callElement: KtElement,
    explicitReceiver: KtExpression?,
    visibilityChecker: KaUseSiteVisibilityChecker,
    subtypingErrorTypePolicy: KaSubtypingErrorTypePolicy = KaSubtypingErrorTypePolicy.STRICT,
): Boolean {
    val candidateSymbol = signature.symbol
    if (callElement is KtConstructorDelegationCall) {
        // Exclude caller from candidates for `this(...)` delegated constructor calls.
        // The parent of KaDelegatedConstructorCall should be the KtConstructor. We don't need to get the symbol for the constructor
        // to determine if it's a self-call; we can just compare the candidate's PSI.
        val candidatePsi = candidateSymbol.psi
        if (candidatePsi != null && candidatePsi == callElement.parent) {
            return false
        }
    }

    // We want only the candidates that match the receiver type. E.g., if you have code like this:
    // ```
    // fun String.foo() {}
    // fun Int.foo() {}
    // fun call(i: Int?) {
    //   <expr>i?.foo()</expr>
    // }
    // ```
    // The available candidates are `String.foo()` and `Int.foo()`. When checking the receiver types for safe calls, we want to compare
    // the non-nullable receiver type against the candidate receiver type. E.g., that `Int` (and not the type of `i` which is `Int?`)
    // is subtype of `Int` (the candidate receiver type).
    val receiverTypes = collectReceiverTypesForElement(callElement, explicitReceiver)

    val candidateReceiverType = signature.receiverType
    if (candidateReceiverType != null && receiverTypes.none {
            it.isSubtypeOf(candidateReceiverType, subtypingErrorTypePolicy) ||
                    it.nullability != KaTypeNullability.NON_NULLABLE && it.withNullability(KaTypeNullability.NON_NULLABLE)
                        .isSubtypeOf(candidateReceiverType, subtypingErrorTypePolicy)
        }
    ) return false

    // Filter out candidates not visible from call site
    if (!visibilityChecker.isVisible(candidateSymbol)) return false

    return true
}

/**
 * If there is no explicit receiver, obtains scope context for [callElement] and returns implicit types from the context.
 * If explicit receiver is present and can be resolved, returns its type. Otherwise, returns empty list.
 */
context(KaSession)
fun collectReceiverTypesForElement(callElement: KtElement, explicitReceiver: KtExpression?): List<KaType> {
    return if (explicitReceiver != null) {
        collectReceiverTypesForExplicitReceiverExpression(explicitReceiver)
    } else {
        val scopeContext = callElement.containingKtFile.scopeContext(callElement)
        scopeContext.implicitReceivers.map { it.type }
    }
}

context(KaSession)
fun collectReceiverTypesForExplicitReceiverExpression(explicitReceiver: KtExpression): List<KaType> {
    explicitReceiver.referenceExpression()?.mainReference?.let { receiverReference ->
        val receiverSymbol = receiverReference.resolveToExpandedSymbol()
        if (receiverSymbol == null || receiverSymbol is KaPackageSymbol) return emptyList()

        if (receiverSymbol is KaNamedClassSymbol && explicitReceiver.parent is KtCallableReferenceExpression) {
            val receiverSymbolType = receiverSymbol.buildClassTypeBySymbolWithTypeArgumentsFromExpression(explicitReceiver)
            return listOfNotNull(receiverSymbolType, receiverSymbol.companionObject?.defaultType)
        }
    }

    val isSafeCall = explicitReceiver.parent is KtSafeQualifiedExpression

    val explicitReceiverType = explicitReceiver.expressionType ?: error("Receiver should have a KaType")
    val adjustedType = if (isSafeCall) {
        explicitReceiverType.withNullability(KaTypeNullability.NON_NULLABLE)
    } else {
        explicitReceiverType
    }
    return listOf(adjustedType)
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun KaNamedClassSymbol.buildClassTypeBySymbolWithTypeArgumentsFromExpression(expression: KtExpression): KaType =
    buildClassType(this) {
        if (expression is KtCallExpression) {
            val typeArgumentTypes = expression.typeArguments.map { it.typeReference?.type }
            for (typeArgument in typeArgumentTypes) {
                if (typeArgument != null) {
                    argument(typeArgument)
                } else {
                    argument(buildStarTypeProjection())
                }
            }
        }
    }

private val ARRAY_OF_FUNCTION_NAMES: Set<Name> = setOf(ArrayFqNames.ARRAY_OF_FUNCTION) +
        ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY.values +
        ArrayFqNames.EMPTY_ARRAY

context(KaSession)
fun isArrayOfCall(callElement: KtCallElement): Boolean {
    val resolvedCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return false
    val callableId = resolvedCall.partiallyAppliedSymbol.signature.callableId ?: return false
    return callableId.packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME && callableId.callableName in ARRAY_OF_FUNCTION_NAMES
}

/**
 * @return value of the [JvmName] annotation on [symbol] declaration if present, and `null` otherwise
 */
context(KaSession)
fun getJvmName(symbol: KaAnnotatedSymbol): String? {
    val jvmNameAnnotation = symbol.annotations[JvmStandardClassIds.Annotations.JvmName].firstOrNull() ?: return null
    val annotationValue = jvmNameAnnotation.arguments.singleOrNull()?.expression as? KaAnnotationValue.ConstantValue ?: return null
    val stringValue = annotationValue.value as? KaConstantValue.StringValue ?: return null
    return stringValue.value
}

context(KaSession)
fun KtReference.resolveToExpandedSymbol(): KaSymbol? = when (val symbol = resolveToSymbol()) {
    is KaTypeAliasSymbol -> symbol.expandedType.expandedSymbol
    else -> symbol
}

/**
 * @return implicit receivers of [this], including implicit receivers with smart casts, which are unwrapped to [KaImplicitReceiverValue]
 */
fun KaCallableMemberCall<*, *>.getImplicitReceivers(): List<KaImplicitReceiverValue> = partiallyAppliedSymbol
    .let { listOfNotNull(it.dispatchReceiver, it.extensionReceiver) }
    .map { it.unwrapSmartCasts() }
    .filterIsInstance<KaImplicitReceiverValue>()

/**
 * @return receiver value without smart casts if it has any or [this] otherwise
 */
tailrec fun KaReceiverValue.unwrapSmartCasts(): KaReceiverValue = when (this) {
    is KaSmartCastedReceiverValue -> original.unwrapSmartCasts()
    else -> this
}