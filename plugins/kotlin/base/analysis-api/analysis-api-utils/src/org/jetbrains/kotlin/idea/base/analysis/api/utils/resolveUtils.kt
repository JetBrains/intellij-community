// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.kotlin.analysis.api.KaAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.KtCallCandidateInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
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
@OptIn(KaAnalysisApiInternals::class)
fun collectCallCandidates(callElement: KtElement): List<KtCallCandidateInfo> {
    val (candidates, explicitReceiver) = when (callElement) {
        is KtCallElement -> {
            val explicitReceiver = callElement.getQualifiedExpressionForSelector()?.receiverExpression
            callElement.collectCallCandidatesOld() to explicitReceiver
        }

        is KtArrayAccessExpression -> callElement.collectCallCandidatesOld() to callElement.arrayExpression
        else -> return emptyList()
    }

    if (candidates.isEmpty()) return emptyList()
    val fileSymbol = callElement.containingKtFile.let { it.getOriginalKtFile() ?: it }.getFileSymbol()

    return candidates.filter { filterCandidate(it, callElement, fileSymbol, explicitReceiver) }
}

context(KaSession)
private fun filterCandidate(
    candidateInfo: KtCallCandidateInfo,
    callElement: KtElement,
    fileSymbol: KtFileSymbol,
    explicitReceiver: KtExpression?
): Boolean {
    val candidateCall = candidateInfo.candidate
    if (candidateCall !is KaFunctionCall<*>) return false
    val signature = candidateCall.partiallyAppliedSymbol.signature
    return filterCandidateByReceiverTypeAndVisibility(signature, callElement, fileSymbol, explicitReceiver)
}

context(KaSession)
fun filterCandidateByReceiverTypeAndVisibility(
    signature: KtFunctionLikeSignature<KaFunctionLikeSymbol>,
    callElement: KtElement,
    fileSymbol: KtFileSymbol,
    explicitReceiver: KtExpression?,
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
    if (candidateReceiverType != null && receiverTypes.none { it.isSubTypeOf(candidateReceiverType, subtypingErrorTypePolicy) }) return false

    // Filter out candidates not visible from call site
    if (candidateSymbol is KaSymbolWithVisibility && !isVisible(candidateSymbol, fileSymbol, explicitReceiver, callElement)) return false

    return true
}

/**
 * If there is no explicit receiver, obtains scope context for [callElement] and returns implicit types from the context.
 * If explicit receiver is present and can be resolved, returns its type. Otherwise, returns empty list.
 */
context(KaSession)
fun collectReceiverTypesForElement(callElement: KtElement, explicitReceiver: KtExpression?): List<KtType> {
    return if (explicitReceiver != null) {
        collectReceiverTypesForExplicitReceiverExpression(explicitReceiver)
    } else {
        val scopeContext = callElement.containingKtFile.getScopeContextForPosition(callElement)
        scopeContext.implicitReceivers.map { it.type }
    }
}

context(KaSession)
fun collectReceiverTypesForExplicitReceiverExpression(explicitReceiver: KtExpression): List<KtType> {
    explicitReceiver.referenceExpression()?.mainReference?.let { receiverReference ->
        val receiverSymbol = receiverReference.resolveToExpandedSymbol()
        if (receiverSymbol == null || receiverSymbol is KtPackageSymbol) return emptyList()

        if (receiverSymbol is KaNamedClassOrObjectSymbol && explicitReceiver.parent is KtCallableReferenceExpression) {
            val receiverSymbolType = receiverSymbol.buildClassTypeBySymbolWithTypeArgumentsFromExpression(explicitReceiver)
            return listOfNotNull(receiverSymbolType, receiverSymbol.companionObject?.buildSelfClassType())
        }
    }

    val isSafeCall = explicitReceiver.parent is KtSafeQualifiedExpression

    val explicitReceiverType = explicitReceiver.getKtType() ?: error("Receiver should have a KtType")
    val adjustedType = if (isSafeCall) {
        explicitReceiverType.withNullability(KtTypeNullability.NON_NULLABLE)
    } else {
        explicitReceiverType
    }
    return listOf(adjustedType)
}

context(KaSession)
private fun KaNamedClassOrObjectSymbol.buildClassTypeBySymbolWithTypeArgumentsFromExpression(expression: KtExpression): KtType =
    buildClassType(this) {
        if (expression is KtCallExpression) {
            val typeArgumentTypes = expression.typeArguments.map { it.typeReference?.getKtType() }
            for (typeArgument in typeArgumentTypes) {
                if (typeArgument != null) {
                    argument(typeArgument)
                } else {
                    argument(KtStarTypeProjection(token))
                }
            }
        }
    }

private val ARRAY_OF_FUNCTION_NAMES: Set<Name> = setOf(ArrayFqNames.ARRAY_OF_FUNCTION) +
        ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY.values +
        ArrayFqNames.EMPTY_ARRAY

context(KaSession)
fun isArrayOfCall(callElement: KtCallElement): Boolean {
    val resolvedCall = callElement.resolveCallOld()?.singleFunctionCallOrNull() ?: return false
    val callableId = resolvedCall.partiallyAppliedSymbol.signature.callableId ?: return false
    return callableId.packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME && callableId.callableName in ARRAY_OF_FUNCTION_NAMES
}

/**
 * @return value of the [JvmName] annotation on [symbol] declaration if present, and `null` otherwise
 */
context(KaSession)
fun getJvmName(symbol: KaAnnotatedSymbol): String? {
    val jvmNameAnnotation = symbol.annotationsByClassId(JvmStandardClassIds.Annotations.JvmName).firstOrNull() ?: return null
    val annotationValue = jvmNameAnnotation.arguments.singleOrNull()?.expression as? KtConstantAnnotationValue ?: return null
    val stringValue = annotationValue.constantValue as? KaConstantValue.KaStringConstantValue ?: return null
    return stringValue.value
}

context(KaSession)
fun KtReference.resolveToExpandedSymbol(): KtSymbol? = when (val symbol = resolveToSymbol()) {
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

private tailrec fun KaReceiverValue.unwrapSmartCasts(): KaReceiverValue = when (this) {
    is KaSmartCastedReceiverValue -> original.unwrapSmartCasts()
    else -> this
}