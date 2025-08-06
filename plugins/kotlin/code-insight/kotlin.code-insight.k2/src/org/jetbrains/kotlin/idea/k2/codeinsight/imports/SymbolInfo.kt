// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.withClassId

internal sealed interface SymbolInfo {
    val importableName: FqName?

    companion object {
        context(_: KaSession)
        fun create(symbol: KaClassLikeSymbol): SymbolInfo {
            val classImportableName = symbol.classId ?: return UnsupportedSymbolInfo

            return ClassLikeSymbolInfo(classImportableName)
        }

        context(_: KaSession)
        fun create(symbol: KaCallableSymbol, containingClassSymbol: KaClassLikeSymbol?): SymbolInfo {
            val symbolImportableName = computeImportableName(symbol, containingClassSymbol) 
                ?: return UnsupportedSymbolInfo

            return CallableSymbolInfo(symbolImportableName)
        }

        context(_: KaSession)
        fun create(symbol: KaSymbol): SymbolInfo {
            require(symbol !is KaClassLikeSymbol && symbol !is KaCallableSymbol)

            return UnsupportedSymbolInfo
        }
    }
}

internal data class ClassLikeSymbolInfo(
    val importableClassId: ClassId,
) : SymbolInfo {
    override val importableName: FqName 
        get() = importableClassId.asSingleFqName()
}


internal data class CallableSymbolInfo(
    val importableCallableId: CallableId,
) : SymbolInfo {
    override val importableName: FqName 
        get() = importableCallableId.asSingleFqName()
}

/**
 * Default implementation of [SymbolInfo] for any [KaSymbol] which was not handled
 * by more specialized implementations of [SymbolInfo].
 */
internal data object UnsupportedSymbolInfo : SymbolInfo {
    override val importableName: FqName? get() = null
}

context(_: KaSession)
internal fun containingClassSymbol(symbolInfo: SymbolInfo): KaClassLikeSymbol? =
    when (symbolInfo) {
        is CallableSymbolInfo -> symbolInfo.importableCallableId.classId?.let { findClassLike(it) }
        is ClassLikeSymbolInfo -> symbolInfo.importableClassId.outerClassId?.let { findClassLike(it) }
        UnsupportedSymbolInfo -> null
    }

/**
 * A copy of [KaSession.importableFqName] adapted for [CallableId].
 * 
 * [substitutedContainingClass] is used to create [CallableId]s for
 * [KaCallableSymbol]s declared in classes/interfaces, 
 * but dispatched through objects, because [KaCallableSymbol]s
 * cannot represent that on their own (see KT-60775).
 * 
 * Does not handle [org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol]s (yet).
 */
context(_: KaSession)
private fun computeImportableName(
    target: KaCallableSymbol,
    substitutedContainingClass: KaClassLikeSymbol?
): CallableId? {
    if (target.isLocal) return null
    
    val containingClass = substitutedContainingClass 
        ?: target.containingDeclaration as? KaClassLikeSymbol
        ?: return target.callableId
    
    val canBeImported = containingClass.origin.isJavaSourceOrLibrary() && target.isJavaStaticDeclaration() ||
            (containingClass as? KaClassSymbol)?.classKind == KaClassKind.ENUM_CLASS && isEnumStaticMember(target) ||
            (containingClass as? KaClassSymbol)?.classKind?.isObject == true

    val containingClassId = containingClass.classId ?: return null
    
    return if (canBeImported) target.callableId?.withClassId(containingClassId) else null
}
