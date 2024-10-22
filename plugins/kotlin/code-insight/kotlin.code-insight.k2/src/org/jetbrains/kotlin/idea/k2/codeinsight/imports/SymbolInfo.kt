// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.withClassId

internal sealed interface SymbolInfo {
    fun KaSession.computeImportableName(): FqName?
    fun KaSession.containingClassSymbol(): KaClassLikeSymbol?

    fun KaSession.createPointer(): SymbolInfoPointer

    companion object {
        @OptIn(KaExperimentalApi::class)
        fun KaSession.create(symbol: KaClassLikeSymbol): SymbolInfo {
            val classImportableName = computeImportableName(symbol, containingClass = null)

            return ClassLikeSymbolInfo(symbol, classImportableName)
        }

        @OptIn(KaExperimentalApi::class)
        fun KaSession.create(symbol: KaCallableSymbol, containingClassSymbol: KaClassLikeSymbol?): SymbolInfo {
            val symbolImportableName = computeImportableName(symbol, containingClassSymbol)

            return CallableSymbolInfo(symbol, containingClassSymbol, symbolImportableName)
        }

        @OptIn(KaExperimentalApi::class)
        fun KaSession.create(symbol: KaSymbol): SymbolInfo {
            require(symbol !is KaClassLikeSymbol && symbol !is KaCallableSymbol)

            return UnsupportedSymbolInfo(symbol)
        }
    }
}

internal sealed interface SymbolInfoPointer {
    fun KaSession.restore(): SymbolInfo?
}

internal data class ClassLikeSymbolInfo(
    private val symbol: KaClassLikeSymbol,
    private val importableFqName: FqName?,
): SymbolInfo {
    override fun KaSession.computeImportableName(): FqName? = importableFqName

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return containingDeclarationPatched(symbol) as? KaClassLikeSymbol
    }

    override fun KaSession.createPointer(): SymbolInfoPointer {
        return Pointer(symbol.createPointer(), importableFqName)
    }

    private class Pointer(private val symbolPointer: KaSymbolPointer<KaClassLikeSymbol>, private val importableFqName: FqName?) : SymbolInfoPointer {
        override fun KaSession.restore(): ClassLikeSymbolInfo? {
            val symbol = symbolPointer.restoreSymbol() ?: return null
            return ClassLikeSymbolInfo(symbol, importableFqName)
        }
    }
}


internal data class CallableSymbolInfo(
    private val symbol: KaCallableSymbol,
    private val containingClassSymbol: KaClassLikeSymbol?,
    private val importableFqName: FqName?,
) : SymbolInfo {
    override fun KaSession.computeImportableName(): FqName? = importableFqName

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return containingClassSymbol ?: (containingDeclarationPatched(symbol) as? KaClassLikeSymbol)
    }

    override fun KaSession.createPointer(): SymbolInfoPointer {
        return Pointer(symbol.createPointer(), containingClassSymbol?.createPointer(), importableFqName)
    }

    private class Pointer(
        private val symbolPointer: KaSymbolPointer<KaCallableSymbol>,
        private val containingClassPointer: KaSymbolPointer<KaClassLikeSymbol>?,
        private val importableFqName: FqName?,
    ) : SymbolInfoPointer {
        override fun KaSession.restore(): CallableSymbolInfo? {
            val symbol = symbolPointer.restoreSymbol() ?: return null

            val containingClass = if (containingClassPointer != null) {
                containingClassPointer.restoreSymbol() ?: return null
            } else {
                null
            }

            return CallableSymbolInfo(symbol, containingClass, importableFqName)
        }
    }
}

/**
 * Default implementation of [SymbolInfo] for any [KaSymbol] which was not handled
 * by more specialized implementations of [SymbolInfo].
 */
internal data class UnsupportedSymbolInfo(private val symbol: KaSymbol) : SymbolInfo {
    override fun KaSession.computeImportableName(): FqName? = null

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? = null

    override fun KaSession.createPointer(): SymbolInfoPointer {
        return Pointer(symbol.createPointer())
    }

    private class Pointer(private val symbolPointer: KaSymbolPointer<KaSymbol>) : SymbolInfoPointer {
        override fun KaSession.restore(): UnsupportedSymbolInfo? {
            val symbol = symbolPointer.restoreSymbol() ?: return null
            return UnsupportedSymbolInfo(symbol)
        }
    }
}

private fun KaSession.computeImportableName(
    target: KaSymbol,
    containingClass: KaClassLikeSymbol?
): FqName? {
    if (containingClass == null) {
        return target.importableFqName
    }

    if (target !is KaCallableSymbol) return null

    val callableId = target.callableId ?: return null
    if (callableId.classId == null) return null

    val receiverClassId = containingClass.classId ?: return null

    val substitutedCallableId = callableId.withClassId(receiverClassId)

    return substitutedCallableId.asSingleFqName()
}
