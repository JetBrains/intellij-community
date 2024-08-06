// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.withClassId

internal sealed interface ImportableKaSymbol {
    fun KaSession.computeImportableName(): FqName?
    fun KaSession.containingClassSymbol(): KaClassLikeSymbol?

    fun KaSession.createPointer(): ImportableKaSymbolPointer

    companion object {
        fun create(symbol: KaClassLikeSymbol): ImportableKaSymbol =
            ImportableKaClassLikeSymbol(symbol)

        fun create(symbol: KaCallableSymbol, containingClassSymbol: KaClassLikeSymbol?): ImportableKaSymbol =
            ImportableKaCallableSymbol(symbol, containingClassSymbol)
    }
}

internal sealed interface ImportableKaSymbolPointer {
    fun KaSession.restore(): ImportableKaSymbol?
}

internal data class ImportableKaClassLikeSymbol(
    private val symbol: KaClassLikeSymbol,
): ImportableKaSymbol {
    override fun KaSession.computeImportableName(): FqName? {
        return symbol.classId?.asSingleFqName()
    }

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return symbol.containingSymbol as? KaClassLikeSymbol
    }

    override fun KaSession.createPointer(): ImportableKaSymbolPointer {
        return Pointer(symbol.createPointer())
    }

    private class Pointer(private val symbolPointer: KaSymbolPointer<KaClassLikeSymbol>) : ImportableKaSymbolPointer {
        override fun KaSession.restore(): ImportableKaClassLikeSymbol? {
            val symbol = symbolPointer.restoreSymbol() ?: return null
            return ImportableKaClassLikeSymbol(symbol)
        }
    }
}


internal data class ImportableKaCallableSymbol(
    private val symbol: KaCallableSymbol,
    private val containingClassSymbol: KaClassLikeSymbol?
) : ImportableKaSymbol {
    override fun KaSession.computeImportableName(): FqName? {
        return computeImportableName(symbol, containingClassSymbol)
    }

    override fun KaSession.containingClassSymbol(): KaClassLikeSymbol? {
        return containingClassSymbol ?: (symbol.containingSymbol as? KaClassLikeSymbol)
    }

    override fun KaSession.createPointer(): ImportableKaSymbolPointer {
        return Pointer(symbol.createPointer(), containingClassSymbol?.createPointer())
    }

    private class Pointer(
        private val symbolPointer: KaSymbolPointer<KaCallableSymbol>,
        private val containingClassPointer: KaSymbolPointer<KaClassLikeSymbol>?
    ) : ImportableKaSymbolPointer {
        override fun KaSession.restore(): ImportableKaCallableSymbol? {
            val symbol = symbolPointer.restoreSymbol() ?: return null

            val containingClass = if (containingClassPointer != null) {
                containingClassPointer.restoreSymbol() ?: return null
            } else {
                null
            }

            return ImportableKaCallableSymbol(symbol, containingClass)
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
