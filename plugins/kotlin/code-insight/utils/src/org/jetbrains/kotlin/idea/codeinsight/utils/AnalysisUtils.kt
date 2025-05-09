// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.util.OperatorNameConventions

fun KtDotQualifiedExpression.isToString(): Boolean {
    val callExpression = selectorExpression as? KtCallExpression ?: return false
    if (callExpression.valueArguments.isNotEmpty()) return false
    val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
    if (referenceExpression.getReferencedName() != OperatorNameConventions.TO_STRING.asString()) return false
    return analyze(callExpression) {
        referenceExpression.mainReference.resolveToSymbols().any { symbol ->
            val functionSymbol = symbol as? KaNamedFunctionSymbol ?: return@any false
            functionSymbol.valueParameters.isEmpty() && functionSymbol.returnType.isStringType
        }
    }
}

context(KaSession)
fun KtDeclaration.isFinalizeMethod(): Boolean {
    if (containingClass() == null) return false
    val function = this as? KtNamedFunction ?: return false
    return function.name == "finalize"
            && function.valueParameters.isEmpty()
            && function.returnType.isUnitType
}

context(KaSession)
fun KaSymbol.getFqNameIfPackageOrNonLocal(): FqName? = when (this) {
    is KaPackageSymbol -> fqName
    is KaCallableSymbol -> callableId?.asSingleFqName()
    is KaClassLikeSymbol -> classId?.asSingleFqName()
    else -> null
}

context(KaSession)
fun KtCallExpression.isArrayOfFunction(): Boolean {
    val functionNames = ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY.values.toSet() +
            ArrayFqNames.ARRAY_OF_FUNCTION +
            ArrayFqNames.EMPTY_ARRAY

    val call = resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.callableId ?: return false

    return call.packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME &&
            functionNames.contains(call.callableName)
}

/**
 * Determines whether [this] call expression is an implicit `invoke` operator call.
 *
 * @return `true` if the expression is an implicit `invoke` call, `false` if it is not,
 * and `null` if the function resolve was unsuccessful.
 */
context(KaSession)
fun KtCallExpression.isImplicitInvokeCall(): Boolean? {
    val functionCall = this.resolveToCall()?.singleFunctionCallOrNull() ?: return null

    return functionCall is KaSimpleFunctionCall && functionCall.isImplicitInvoke
}

/**
 * Returns containing class symbol, if [reference] is a short reference to a companion object. Otherwise, returns null.
 * For example:
 * ```
 * class A {
 *      companion object {
 *           fun foo() {}
 *      }
 * }
 *
 * fun main() {
 *      A.foo() // symbol for `A`, and not for `A.Companion`, is returned
 * }
 * ```
 */
context(KaSession)
fun KtReference.resolveCompanionObjectShortReferenceToContainingClassSymbol(): KaNamedClassSymbol? {
    if (this !is KtSimpleNameReference) return null

    val symbol = this.resolveToSymbol()
    if (symbol !is KaClassSymbol || symbol.classKind != KaClassKind.COMPANION_OBJECT) return null

    // class name reference resolves to companion
    if (expression.name == symbol.name?.asString()) return null

    val containingSymbol = symbol.containingDeclaration as? KaNamedClassSymbol
    return containingSymbol?.takeIf { it.companionObject == symbol }
}

/**
 * Checks whether [this] is one of the following:
 * * extension
 * * variable having a return type with a receiver
 */
context(KaSession)
fun KaCallableSymbol.canBeUsedAsExtension(): Boolean =
    isExtension || this is KaVariableSymbol && (returnType as? KaFunctionType)?.hasReceiver == true

context (KaSession)
fun KtExpression.resolveExpression(): KaSymbol? {
    val reference = mainReference?:(this as? KtThisExpression)?.instanceReference?.mainReference
    reference?.resolveToSymbol()?.let { return it }
    val call = resolveToCall()?.calls?.singleOrNull() ?: return null
    return if (call is KaCallableMemberCall<*, *>) call.symbol else null
}

/**
 * A less fragile alternative to [KaSession.type] which should be safer to use on incomplete code. 
 * 
 * Can be used now in cases when exceptions occur too frequently, but should eventually become obsolete,
 * as [KaSession.type] becomes less fragile.
 * 
 * See KT-77222 for more information.
 * 
 * N.B. This function should NOT be used everywhere - only in cases where exceptions are too frequent.
 */
context(KaSession)
@get:ApiStatus.Internal
val KtTypeReference.typeIfSafeToResolve: KaType?
    get() {
        val typeElement = this.typeElement?.unwrapNullability() ?: return null

        if (typeElement !is KtUserType) {
            // We currently do a generous assumption that only `KtUserType`s are really fragile.
            // If proved otherwise, this condition should become more restrictive.
            return this.type
        }

        val typeNameExpression = typeElement.referenceExpression

        if (typeNameExpression?.mainReference?.resolveToSymbols().isNullOrEmpty()) {
            // resolveToSymbols failed, no point in calling type, it may produce/log exceptions 
            return null
        }

        return this.type
    }