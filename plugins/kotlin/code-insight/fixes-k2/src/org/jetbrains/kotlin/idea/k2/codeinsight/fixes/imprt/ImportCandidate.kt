// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.deprecationStatus
import org.jetbrains.kotlin.analysis.api.components.fakeOverrideOriginal
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.withClassId
import org.jetbrains.kotlin.resolve.deprecation.DeprecationInfo

internal sealed interface ImportCandidate {
    val symbol: KaDeclarationSymbol

    context(_: KaSession)
    fun createPointer(): ImportCandidatePointer
}

internal interface ImportCandidatePointer {
    context(_: KaSession)
    fun restore(): ImportCandidate?
}


internal data class ClassLikeImportCandidate(override val symbol: KaClassLikeSymbol) : ImportCandidate {
    context(_: KaSession)
    override fun createPointer(): ImportCandidatePointer = Pointer(symbol.createPointer())

    private class Pointer(private val symbolPointer: KaSymbolPointer<KaClassLikeSymbol>) : ImportCandidatePointer {
        context(session: KaSession)
        override fun restore(): ImportCandidate? {
            val symbol = with(session) { symbolPointer.restoreSymbol() } ?: return null
            return ClassLikeImportCandidate(symbol)
        }
    }
}

@ConsistentCopyVisibility
internal data class CallableImportCandidate private constructor(
    override val symbol: KaCallableSymbol,
    val dispatcherObject: KaClassSymbol?,
) : ImportCandidate {

    context(_: KaSession)
    override fun createPointer(): ImportCandidatePointer = Pointer(symbol.createPointer(), dispatcherObject?.createPointer())

    private class Pointer(
        val symbolPointer: KaSymbolPointer<KaCallableSymbol>,
        val dispatcherObjectPointer: KaSymbolPointer<KaClassSymbol>?,
    ) : ImportCandidatePointer {

        context(session: KaSession)
        override fun restore(): ImportCandidate? {
            val symbol = with(session) { symbolPointer.restoreSymbol() } ?: return null

            if (dispatcherObjectPointer == null) return create(symbol)

            val dispatcherObject = with(session) { dispatcherObjectPointer.restoreSymbol() } ?: return null
            return create(symbol, dispatcherObject)
        }
    }

    companion object {
        context(_: KaSession)
        fun create(
            symbol: KaCallableSymbol,
            dispatcherObject: KaClassSymbol? = null,
        ): CallableImportCandidate {
            return CallableImportCandidate(
                symbol,
                dispatcherObject?.takeUnless { it == symbol.containingSymbol },
            )
        }
    }
}

internal val ImportCandidate.name: Name
    get() = (symbol as KaNamedSymbol).name

context(_: KaSession)
internal val ImportCandidate.fqName: FqName?
    get() = when (this) {
        is CallableImportCandidate -> callableId?.asSingleFqName()
        is ClassLikeImportCandidate -> classId?.asSingleFqName()
    }

internal val ImportCandidate.packageName: FqName?
    get() = when (this) {
        is CallableImportCandidate -> callableId?.packageName
        is ClassLikeImportCandidate -> classId?.packageFqName
    }

internal val ClassLikeImportCandidate.classId: ClassId?
    get() = symbol.classId

internal val CallableImportCandidate.callableId: CallableId?
    get() {
        val originalId = symbol.callableId

        val dispatchObjectId = dispatcherObject?.classId

        return if (dispatchObjectId != null) {
            originalId?.withClassId(dispatchObjectId)
        } else {
            originalId
        }
    }

internal val ImportCandidate.psi: PsiElement?
    get() = when (this) {
        is CallableImportCandidate -> {
            // An existing PSI is really important for the auto-import candidate selection popup. 
            // So, even if there is a dispatcher object, we still return the PSI of the original method.
            symbol.psi
        }

        is ClassLikeImportCandidate -> symbol.psi
    }

context(_: KaSession)
@KaExperimentalApi
internal val ImportCandidate.deprecationStatus: DeprecationInfo?
    get() = symbol.deprecationStatus

context(_: KaSession)
internal val CallableImportCandidate.receiverType: KaType?
    get() = symbol.receiverType

context(_: KaSession)
internal val CallableImportCandidate.containingClass: KaClassSymbol?
    get() = dispatcherObject
        ?: symbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol
