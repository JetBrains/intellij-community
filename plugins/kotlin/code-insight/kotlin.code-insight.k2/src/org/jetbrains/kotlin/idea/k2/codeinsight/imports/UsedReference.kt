// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.codeinsight.utils.isUnaryOperatorOnIntLiteralReference
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtDefaultAnnotationArgumentReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile

internal class UsedReference private constructor(val reference: KtReference) {
    context(_: KaSession)
    fun resolvesByNames(): Collection<Name> {
        if (reference is KDocReference && !isResolved()) {
            // if KDoc reference is unresolved, do not consider it to be an unresolved symbol (see KT-61785)
            return emptyList()
        }

        return reference.resolvesByNames
    }

    context(_: KaSession)
    fun isResolved(): Boolean {
        if (isEmptyInvokeReference(reference)) {
            // we consider "empty" invoke references to be resolved,
            // but they will yield no symbols from `resolveToReferencedSymbols()`
            return true
        }

        val resolvedSymbols = reference.resolveToSymbols()

        if (reference is KtInvokeFunctionReference) {
            // invoke references on Kotlin builtin functional types (like `() -> Unit`)
            // always have empty `resolveToSymbols`, so we have to do the check another way
            val callInfo = reference.element.resolveToCall() ?: return false

            return callInfo.calls.isNotEmpty()
        }

        return resolvedSymbols.isNotEmpty()
    }

    context(_: KaSession)
    fun resolveToReferencedSymbols(): Collection<ReferencedSymbol> {
        val symbols = reference.resolveToSymbols()
        return symbols.mapNotNull { adjustSymbolIfNeeded(it, reference) }.map { ReferencedSymbol(reference, it) }
    }

    companion object {
        context(_: KaSession)
        fun createFrom(reference: KtReference): UsedReference? {
            return when {
                isDefaultJavaAnnotationArgumentReference(reference) -> null
                isUnaryOperatorOnIntLiteralReference(reference) -> null
                else -> UsedReference(reference)
            }
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
 * In K2, every call in the form of `foo()` has `KtInvokeFunctionReference` on it.
 *
 * In the cases when `foo()` call is not actually an `invoke` call, we do not want to process such references,
 * since they are not supposed to resolve anywhere.
 */
context(_: KaSession)
private fun isEmptyInvokeReference(reference: KtReference): Boolean {
    if (reference !is KtInvokeFunctionReference) return false

    val callInfo = reference.element.resolveToCall()
    val isImplicitInvoke = callInfo?.calls?.any { it is KaSimpleFunctionCall && it.isImplicitInvoke } == true

    return !isImplicitInvoke
}

/**
 * Provides a better, more precise alternative to [target] symbol if necessary.
 */
context(_: KaSession)
private fun adjustSymbolIfNeeded(
    target: KaSymbol,
    reference: KtReference,
    containingFile: KtFile = reference.element.containingKtFile,
): KaSymbol? = when {
    reference.isImplicitReferenceToCompanion() && target !is KaTypeAliasSymbol -> {
        (target as? KaNamedClassSymbol)?.containingSymbol
    }

    target is KaConstructorSymbol -> {
        val targetClass = target.containingSymbol as? KaClassLikeSymbol

        // if constructor leads to inner class, it cannot be resolved by import
        targetClass?.takeUnless { it is KaNamedClassSymbol && it.isInner }
    }

    target is KaSamConstructorSymbol -> {
        val samClass = target.constructedClass

        resolveTypeAliasedConstructorReference(reference, samClass, containingFile) ?: samClass
    }

    else -> target
}

