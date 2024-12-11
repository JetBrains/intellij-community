// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.withClassId

internal sealed interface SymbolInfo {
    fun KaSession.computeImportableName(): FqName?
    fun KaSession.containingClassSymbol(): KaClassLikeSymbol?

    fun KaSession.createPointer(): SymbolInfoPointer

    companion object {
        @OptIn(KaExperimentalApi::class)
        fun KaSession.create(symbol: KaClassLikeSymbol): SymbolInfo {
            val classImportableName = symbol.classId ?: return UnsupportedSymbolInfo

            return ClassLikeSymbolInfo(classImportableName)
        }

        @OptIn(KaExperimentalApi::class)
        fun KaSession.create(symbol: KaCallableSymbol, containingClassSymbol: KaClassLikeSymbol?): SymbolInfo {
            val symbolImportableName = computeImportableName(symbol, containingClassSymbol) 
                ?: return UnsupportedSymbolInfo

            return CallableSymbolInfo(symbolImportableName)
        }

        @OptIn(KaExperimentalApi::class)
        fun KaSession.create(symbol: KaSymbol): SymbolInfo {
            require(symbol !is KaClassLikeSymbol && symbol !is KaCallableSymbol)

            return UnsupportedSymbolInfo
        }
    }
}

internal sealed interface SymbolInfoPointer {
    fun KaSession.restore(): SymbolInfo?
}

internal data class ClassLikeSymbolInfo(
    private val importableClassId: ClassId,
) : SymbolInfo, SymbolInfoPointer {
    override fun KaSession.computeImportableName(): FqName? = importableClassId.asSingleFqName()

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return importableClassId.outerClassId?.let(::findClassLike)
    }

    override fun KaSession.createPointer(): SymbolInfoPointer {
        return this@ClassLikeSymbolInfo
    }

    override fun KaSession.restore(): SymbolInfo? {
        return this@ClassLikeSymbolInfo
    }
}


internal data class CallableSymbolInfo(
    private val importableFqName: CallableId,
) : SymbolInfo, SymbolInfoPointer {
    override fun KaSession.computeImportableName(): FqName? = importableFqName.asSingleFqName()

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return importableFqName.classId?.let(::findClassLike)
    }

    override fun KaSession.createPointer(): SymbolInfoPointer {
        return this@CallableSymbolInfo
    }

    override fun KaSession.restore(): SymbolInfo? {
        return this@CallableSymbolInfo
    }
}

/**
 * Default implementation of [SymbolInfo] for any [KaSymbol] which was not handled
 * by more specialized implementations of [SymbolInfo].
 */
internal data object UnsupportedSymbolInfo : SymbolInfo, SymbolInfoPointer {
    override fun KaSession.computeImportableName(): FqName? = null

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? = null

    override fun KaSession.createPointer(): SymbolInfoPointer {
        return this@UnsupportedSymbolInfo
    }

    override fun KaSession.restore(): SymbolInfo? {
        return this@UnsupportedSymbolInfo
    }
}

private fun KaSession.computeImportableName(
    target: KaCallableSymbol,
    containingClass: KaClassLikeSymbol?
): CallableId? {
    if (target is KaReceiverParameterSymbol) {
        return null
    }

    if (containingClass == null) {
        return target.callableId
    }

    val callableId = target.callableId ?: return null
    if (callableId.classId == null) return null

    val receiverClassId = containingClass.classId ?: return null

    val substitutedCallableId = callableId.withClassId(receiverClassId)

    return substitutedCallableId
}
