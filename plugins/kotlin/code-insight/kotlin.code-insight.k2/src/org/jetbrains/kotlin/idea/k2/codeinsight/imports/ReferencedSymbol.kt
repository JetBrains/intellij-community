// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

internal class ReferencedSymbol(val reference: KtReference, val symbol: KaSymbol) {
    fun KaSession.computeImportableFqName(): FqName {
        return toImportableKaSymbol().run { computeImportableName() }
    }

    fun KaSession.isResolvedWithImport(): Boolean {
        if (definitelyNotImported) return false

        val isNotAliased = symbol.name in reference.resolvesByNames

        if (isNotAliased && isAccessibleAsMemberCallable(symbol, reference.element)) return false
        if (isNotAliased && isAccessibleAsMemberClassifier(symbol, reference.element)) return false

        return canBeResolvedViaImport(reference, symbol)
    }

    private val KaSession.definitelyNotImported: Boolean get() = when {
        symbol.isLocal -> true

        symbol is KaPackageSymbol -> true
        symbol is KaReceiverParameterSymbol -> true
        symbol is KaTypeParameterSymbol -> true

        else -> false
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

    if (element is KtForExpression || element is KtPropertyDelegate) {
        // approximation until KT-70521 is fixed,
        // and dispatcher receiver can be analyzed for such cases
        return true
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