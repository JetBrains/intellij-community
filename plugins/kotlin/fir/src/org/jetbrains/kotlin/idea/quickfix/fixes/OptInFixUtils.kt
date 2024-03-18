// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal object OptInFixUtils {
    fun optInMarkerClassId(diagnostic: KtFirDiagnostic<PsiElement>): ClassId? = when (diagnostic) {
        is KtFirDiagnostic.OptInUsage -> diagnostic.optInMarkerClassId
        is KtFirDiagnostic.OptInUsageError -> diagnostic.optInMarkerClassId
        is KtFirDiagnostic.OptInOverride -> diagnostic.optInMarkerClassId
        is KtFirDiagnostic.OptInOverrideError -> diagnostic.optInMarkerClassId
        else -> null
    }

    context (KtAnalysisSession)
    fun optInFqName(): FqName? = OptInNames.OPT_IN_FQ_NAME.takeIf { it.annotationApplicable() }
        ?: FqNames.OptInFqNames.OLD_USE_EXPERIMENTAL_FQ_NAME.takeIf { it.annotationApplicable() }

    context (KtAnalysisSession)
    private fun FqName.annotationApplicable(): Boolean =
        getClassOrObjectSymbolByClassId(ClassId.topLevel(this)) != null

    context (KtAnalysisSession)
    fun findAnnotation(classId: ClassId): KtNamedClassOrObjectSymbol? =
        getClassOrObjectSymbolByClassId(classId) as? KtNamedClassOrObjectSymbol

    context (KtAnalysisSession)
    fun annotationIsVisible(annotation: KtNamedClassOrObjectSymbol, from: KtElement): Boolean {
        val file = from.containingKtFile.getFileSymbol()
        return isVisible(annotation, file, receiverExpression = null, from)
    }
}