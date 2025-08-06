// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
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
    context(_: KaSession)
    fun computeImportableName(): FqName? {
        return toSymbolInfo().importableName
    }

    context(_: KaSession)
    fun isResolvedWithImport(): Boolean {
        if (definitelyNotImported) return false

        val isNotAliased = symbol.name in reference.resolvesByNames

        if (isNotAliased && isAccessibleAsMemberCallable(symbol, reference.element)) return false
        if (isNotAliased && isAccessibleAsMemberClassifier(symbol, reference.element)) return false

        return canBeResolvedViaImport(reference, symbol)
    }

    context(_: KaSession)
    private val definitelyNotImported: Boolean get() = when {
        symbol.isLocal -> true

        // symbols from <dynamic> scope cannot be imported,
        // they do not have stable identities and FQNs
        symbol.origin == KaSymbolOrigin.JS_DYNAMIC -> true

        symbol is KaPackageSymbol -> true
        symbol is KaReceiverParameterSymbol -> true
        symbol is KaTypeParameterSymbol -> true

        else -> false
    }

    context(_: KaSession)
    fun toSymbolInfo(): SymbolInfo {
        return when (symbol) {
            is KaCallableSymbol -> {
                val dispatcherReceiver = resolveDispatchReceiver(reference.element) as? KaImplicitReceiverValue
                val containingClassSymbol = dispatcherReceiver?.symbol as? KaClassLikeSymbol

                SymbolInfo.create(symbol, containingClassSymbol)
            }

            is KaClassLikeSymbol -> SymbolInfo.create(symbol)

            else -> SymbolInfo.create(symbol)
        }
    }
}

/**
 * We want to skipp the calls which require implicit receiver to be dispatched.
 */
context(_: KaSession)
private fun isDispatchedCall(
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

context(_: KaSession)
private fun isAccessibleAsMemberCallable(
    symbol: KaSymbol,
    element: KtElement,
): Boolean {
    if (symbol !is KaCallableSymbol || containingDeclarationPatched(symbol) !is KaClassLikeSymbol) return false

    if (isEnumStaticMember(symbol)) {
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
context(_: KaSession)
private fun isStaticallyImportedReceiver(
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

context(_: KaSession)
private fun resolveDispatchReceiver(element: KtElement): KaReceiverValue? {
    val adjustedElement = element.callableReferenceExpressionForCallableReference() ?: element
    val dispatchReceiver = adjustedElement.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver

    return dispatchReceiver
}

context(_: KaSession)
private fun canBeResolvedViaImport(reference: KtReference, target: KaSymbol): Boolean {
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
        if (isTypeAliasedInnerClassConstructorCall(target, reference)) {
            /*
            Constructor call of inner class CANNOT be fully qualified,
            it has to be called by its short name with the outer class as receiver.
            
            The same goes for the callable references to such constructors.
            
            We filter regular, not typealiased inner class constructors earlier, in `adjustSymbolIfNeeded`,
            because they cannot ever be resolved via imports.
            
            However, typealiased inner classes constructors CAN rely on the fact that the typealias
            is actually imported.
            
            Hence, if there is an explicit receiver, and the call resolves to a typealiased inner class constructor,
            then we have to consider that this typealias has to be imported somehow.
            */
            return true
        }
        
        val extensionReceiver = resolveExtensionReceiverForFunctionalTypeVariable(referenceExpression, target)
        return extensionReceiver?.expression == explicitReceiver
    }

    return true
}


/**
 * Determines whether the given [reference] resolves to a constructor call or callable reference 
 * of a type-aliased inner class.
 */
context(_: KaSession)
private fun isTypeAliasedInnerClassConstructorCall(target: KaSymbol, reference: KtReference): Boolean {
    return target is KaTypeAliasSymbol &&
            (target.expandedType.symbol as? KaNamedClassSymbol)?.isInner == true &&
            reference.resolveToSymbols().any { it is KaConstructorSymbol && it.containingDeclaration == target }
}

context(_: KaSession)
private fun resolveExtensionReceiverForFunctionalTypeVariable(
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

context(_: KaSession)
private fun canBeResolvedViaImport(reference: KDocReference, target: KaSymbol): Boolean {
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