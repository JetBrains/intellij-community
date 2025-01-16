// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.deprecation.DeprecationInfo

internal sealed interface ImportCandidate {
    val symbol: KaDeclarationSymbol
}

internal data class ClassLikeImportCandidate(override val symbol: KaClassLikeSymbol): ImportCandidate

internal data class CallableImportCandidate(override val symbol: KaCallableSymbol): ImportCandidate

internal val ImportCandidate.name: Name
    get() = (symbol as KaNamedSymbol).name

context(KaSession)
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
    get() = symbol.callableId
        
internal val ImportCandidate.psi: PsiElement?
    get() = symbol.psi

context(KaSession)
@KaExperimentalApi
internal val ImportCandidate.deprecationStatus: DeprecationInfo?
    get() = symbol.deprecationStatus

context(KaSession)
internal val CallableImportCandidate.receiverType: KaType?
    get() = symbol.receiverType

context(KaSession)
internal val CallableImportCandidate.containingClass: KaClassSymbol?
    get() = symbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol
