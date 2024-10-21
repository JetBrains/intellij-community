// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.withClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.unwrapParenthesesLabelsAndAnnotations

internal class UsedReference private constructor(val reference: KtReference) {
    fun KaSession.resolvesByNames(): Collection<Name> {
        if (reference is KDocReference && !isResolved()) {
            // if KDoc reference is unresolved, do not consider it to be an unresolved symbol (see KT-61785)
            return emptyList()
        }

        return reference.resolvesByNames
    }

    fun KaSession.isResolved(): Boolean {
        if (reference is KtInvokeFunctionReference) {
            // invoke references on Kotlin builtin functional types (like `() -> Unit`)
            // always have empty `resolveToSymbols`, so we have to do the check another way
            val callInfo = reference.element.resolveToCall() ?: return false

            return callInfo.calls.isNotEmpty()
        }

        val resolvedSymbols = reference.resolveToSymbols()

        return resolvedSymbols.isNotEmpty()
    }

    fun KaSession.resolveToImportableSymbols(): Collection<UsedSymbol> {
        return reference.resolveToSymbols().mapNotNull { adjustSymbolIfNeeded(it, reference) }.map { UsedSymbol(reference, it) }
    }

    companion object {
        fun KaSession.createFrom(reference: KtReference): UsedReference? {
            return when {
                isDefaultJavaAnnotationArgumentReference(reference) -> null
                isUnaryOperatorOnIntLiteralReference(reference) -> null
                isEmptyInvokeReference(reference) -> null
                else -> UsedReference(reference)
            }
        }
    }
}

internal class UsedSymbol(val reference: KtReference, val symbol: KaSymbol) {
    fun KaSession.computeImportableFqName(): FqName? {
        return computeImportableName(symbol, resolveDispatchReceiver(reference.element) as? KaImplicitReceiverValue?)
    }

    fun KaSession.isResolvedWithImport(): Boolean {
        if (symbol is KaReceiverParameterSymbol) return false

        val isNotAliased = symbol.name in reference.resolvesByNames

        if (isNotAliased && isAccessibleAsMemberCallable(symbol, reference.element)) return false
        if (isNotAliased && isAccessibleAsMemberClassifier(symbol, reference.element)) return false

        return canBeResolvedViaImport(reference, symbol)
    }

    fun KaSession.toImportableKaSymbol(): ImportableKaSymbol {
        return when (symbol) {
            is KaCallableSymbol -> {
                val dispatcherReceiver = resolveDispatchReceiver(reference.element) as? KaImplicitReceiverValue
                val containingClassSymbol = dispatcherReceiver?.symbol as? KaClassLikeSymbol

                ImportableKaSymbol.run { create(symbol, containingClassSymbol) }
            }

            is KaClassLikeSymbol -> ImportableKaSymbol.run { create(symbol) }

            else -> error("Unexpected symbol type ${symbol::class}")
        }
    }
}

/**
 * Currently, such references do not properly resolve to symbols (see KT-70476).
 *
 * Overall, such references cannot really influence the import optimization process.
 */
private fun isDefaultJavaAnnotationArgumentReference(reference: KtReference): Boolean {
    return reference is KtDefaultAnnotationArgumentReference
}

/**
 * Checks if the [reference] points to unary plus or minus operator on an [Int] literal, like `-10` or `+(20)`.
 *
 * Currently, such operators are not properly resolved in K2 Mode (see KT-70774).
 */
private fun isUnaryOperatorOnIntLiteralReference(reference: KtReference): Boolean {
    val unaryOperationReferenceExpression = reference.element as? KtOperationReferenceExpression ?: return false

    if (unaryOperationReferenceExpression.operationSignTokenType !in arrayOf(KtTokens.PLUS, KtTokens.MINUS)) return false

    val prefixExpression = unaryOperationReferenceExpression.parent as? KtUnaryExpression ?: return false
    val unwrappedBaseExpression = prefixExpression.baseExpression?.unwrapParenthesesLabelsAndAnnotations() ?: return false

    return unwrappedBaseExpression is KtConstantExpression &&
            unwrappedBaseExpression.elementType == KtNodeTypes.INTEGER_CONSTANT
}

/**
 * In K2, every call in the form of `foo()` has `KtInvokeFunctionReference` on it.
 *
 * In the cases when `foo()` call is not actually an `invoke` call, we do not want to process such references,
 * since they are not supposed to resolve anywhere.
 */
private fun KaSession.isEmptyInvokeReference(reference: KtReference): Boolean {
    if (reference !is KtInvokeFunctionReference) return false

    val callInfo = reference.element.resolveToCall()
    val isImplicitInvoke = callInfo?.calls?.any { it is KaSimpleFunctionCall && it.isImplicitInvoke } == true

    return !isImplicitInvoke
}

/**
 * Provides a better, more precise alternative to [target] symbol if necessary.
 */
private fun KaSession.adjustSymbolIfNeeded(
    target: KaSymbol,
    reference: KtReference,
    containingFile: KtFile = reference.element.containingKtFile,
): KaSymbol? = when {
    reference.isImplicitReferenceToCompanion() -> {
        (target as? KaNamedClassSymbol)?.containingSymbol
    }

    target is KaConstructorSymbol -> {
        val targetClass = target.containingSymbol as? KaClassLikeSymbol

        // if constructor is typealiased, it can be imported in any scenario
        val typeAlias = targetClass?.let { resolveTypeAliasedConstructorReference(reference, it, containingFile) }

        // if constructor leads to inner class, it cannot be resolved by import
        val notInnerTargetClass = targetClass?.takeUnless { it is KaNamedClassSymbol && it.isInner }

        typeAlias ?: notInnerTargetClass
    }

    target is KaSamConstructorSymbol -> {
        val targetClass = findSamClassFor(target)

        targetClass?.let { resolveTypeAliasedConstructorReference(reference, it, containingFile) } ?: targetClass
    }

    else -> target
}

/**
 * We want to skipp the calls which require implicit receiver to be dispatched.
 */
private fun KaSession.isDispatchedCall(
    element: KtElement,
    symbol: KaCallableSymbol,
    dispatchReceiver: KaReceiverValue,
): Boolean {
    return when (dispatchReceiver) {
        is KaExplicitReceiverValue -> true

        is KaSmartCastedReceiverValue -> isDispatchedCall(element, symbol, dispatchReceiver.original)

        is KaImplicitReceiverValue -> !isStaticallyImportedReceiver(element, symbol, dispatchReceiver)
    }
}

private fun KaSession.isAccessibleAsMemberCallable(
    symbol: KaSymbol,
    element: KtElement,
): Boolean {
    if (symbol !is KaCallableSymbol || containingDeclarationPatched(symbol) !is KaClassLikeSymbol) return false

    if (symbol is KaEnumEntrySymbol) {
        return isAccessibleAsMemberCallableDeclaration(symbol, element)
    }

    val dispatchReceiver = resolveDispatchReceiver(element) ?: return false

    return isDispatchedCall(element, symbol, dispatchReceiver)
}

/**
 * Checks if [implicitDispatchReceiver] is introduced via static import
 * from Kotlin object or Java class.
 */
private fun KaSession.isStaticallyImportedReceiver(
    element: KtElement,
    symbol: KaCallableSymbol,
    implicitDispatchReceiver: KaImplicitReceiverValue,
): Boolean {
    val receiverTypeSymbol = implicitDispatchReceiver.type.symbol ?: return false
    val receiverIsObject = receiverTypeSymbol is KaClassSymbol && receiverTypeSymbol.classKind.isObject

    // with static imports, the implicit receiver is either some object symbol or `Unit` in case of imports from Java classes
    if (!receiverIsObject) return false

    return if (symbol.isJavaStaticDeclaration()) {
        !isAccessibleAsMemberCallableDeclaration(symbol, element)
    } else {
        !typeIsPresentAsImplicitReceiver(implicitDispatchReceiver.type, element)
    }
}

private fun KaSession.resolveDispatchReceiver(element: KtElement): KaReceiverValue? {
    val adjustedElement = element.callableReferenceExpressionForCallableReference() ?: element
    val dispatchReceiver = adjustedElement.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver

    return dispatchReceiver
}

private fun KaSession.computeImportableName(
    target: KaSymbol,
    implicitDispatchReceiver: KaImplicitReceiverValue? // TODO: support other types of dispatcher values
): FqName? {
    if (implicitDispatchReceiver == null) {
        return target.importableFqName
    }

    if (target !is KaCallableSymbol) return null

    val callableId = target.callableId ?: return null
    if (callableId.classId == null) return null

    val implicitReceiver = implicitDispatchReceiver.symbol as? KaClassLikeSymbol ?: return null
    val implicitReceiverClassId = implicitReceiver.classId ?: return null

    val substitutedCallableId = callableId.withClassId(implicitReceiverClassId)

    return substitutedCallableId.asSingleFqName()
}

private fun KaSession.canBeResolvedViaImport(reference: KtReference, target: KaSymbol): Boolean {
    if (reference is KDocReference) {
        return canBeResolvedViaImport(reference, target)
    }

    if (target is KaCallableSymbol && target.isExtension) {
        return true
    }

    val referenceExpression = reference.element as? KtNameReferenceExpression

    val explicitReceiver = referenceExpression?.getReceiverExpression()
        ?: referenceExpression?.callableReferenceExpressionForCallableReference()?.receiverExpression

    if (explicitReceiver != null) {
        val extensionReceiver = resolveExtensionReceiverForFunctionalTypeVariable(referenceExpression, target)
        return extensionReceiver?.expression == explicitReceiver
    }

    return true
}

private fun KaSession.resolveExtensionReceiverForFunctionalTypeVariable(
    referenceExpression: KtNameReferenceExpression?,
    target: KaSymbol,
): KaExplicitReceiverValue? {
    val parentCall = referenceExpression?.parent as? KtCallExpression
    val isFunctionalTypeVariable = target is KaPropertySymbol && target.returnType.let { it.isFunctionType || it.isSuspendFunctionType }

    if (parentCall == null || !isFunctionalTypeVariable) {
        return null
    }

    val parentCallInfo = parentCall.resolveToCall()?.singleCallOrNull<KaSimpleFunctionCall>() ?: return null
    if (!parentCallInfo.isImplicitInvoke) return null

    return parentCallInfo.partiallyAppliedSymbol.extensionReceiver as? KaExplicitReceiverValue
}

private fun KaSession.canBeResolvedViaImport(reference: KDocReference, target: KaSymbol): Boolean {
    val qualifier = reference.element.getQualifier() ?: return true

    return if (target is KaCallableSymbol && target.isExtension) {
        val elementHasFunctionDescriptor = reference.element.mainReference.resolveToSymbols().any { it is KaFunctionSymbol }
        val qualifierHasClassDescriptor = qualifier.mainReference.resolveToSymbols().any { it is KaClassLikeSymbol }
        elementHasFunctionDescriptor && qualifierHasClassDescriptor
    } else {
        false
    }
}

private fun KtElement.callableReferenceExpressionForCallableReference(): KtCallableReferenceExpression? =
    (parent as? KtCallableReferenceExpression)?.takeIf { it.callableReference == this }