// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassOrObjectSymbol
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal object OptInFixUtils {
    fun optInMarkerClassId(diagnostic: KaFirDiagnostic<PsiElement>): ClassId? = when (diagnostic) {
        is KaFirDiagnostic.OptInUsage -> diagnostic.optInMarkerClassId
        is KaFirDiagnostic.OptInUsageError -> diagnostic.optInMarkerClassId
        is KaFirDiagnostic.OptInOverride -> diagnostic.optInMarkerClassId
        is KaFirDiagnostic.OptInOverrideError -> diagnostic.optInMarkerClassId
        else -> null
    }

    context (KaSession)
    fun optInFqName(): FqName? = OptInNames.OPT_IN_FQ_NAME.takeIf { it.annotationApplicable() }
        ?: FqNames.OptInFqNames.OLD_USE_EXPERIMENTAL_FQ_NAME.takeIf { it.annotationApplicable() }

    context (KaSession)
    private fun FqName.annotationApplicable(): Boolean =
        getClassOrObjectSymbolByClassId(ClassId.topLevel(this)) != null

    context (KaSession)
    fun findAnnotation(classId: ClassId): KaNamedClassOrObjectSymbol? =
        getClassOrObjectSymbolByClassId(classId) as? KaNamedClassOrObjectSymbol

    context (KaSession)
    fun annotationIsVisible(annotation: KaNamedClassOrObjectSymbol, from: KtElement): Boolean {
        val file = from.containingKtFile.getFileSymbol()
        return isVisible(annotation, file, receiverExpression = null, from)
    }
}