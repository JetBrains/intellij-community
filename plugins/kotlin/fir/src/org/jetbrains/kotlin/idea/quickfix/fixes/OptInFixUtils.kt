// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.resolve.checkers.OptInNames

object OptInFixUtils {
    fun optInMarkerFqName(diagnostic: KtFirDiagnostic<PsiElement>) = when (diagnostic) {
        is KtFirDiagnostic.OptInUsage -> diagnostic.optInMarkerFqName
        is KtFirDiagnostic.OptInUsageError -> diagnostic.optInMarkerFqName
        is KtFirDiagnostic.OptInOverride -> diagnostic.optInMarkerFqName
        is KtFirDiagnostic.OptInOverrideError -> diagnostic.optInMarkerFqName
        else -> null
    }

    context (KtAnalysisSession)
    fun optInFqName(): FqName? = OptInNames.OPT_IN_FQ_NAME.takeIf { it.annotationApplicable() }
        ?: FqNames.OptInFqNames.OLD_USE_EXPERIMENTAL_FQ_NAME.takeIf { it.annotationApplicable() }

    context (KtAnalysisSession)
    private fun FqName.annotationApplicable() = getClassOrObjectSymbolByClassId(ClassId.topLevel(this)) != null

    context (KtAnalysisSession)
    fun isVisible(from: KtElement, to: KtClassOrObjectSymbol): Boolean {
        if (to !is KtSymbolWithVisibility) return false
        val file = from.containingKtFile.getFileSymbol()
        val receiver = (from as? KtQualifiedExpression)?.receiverExpression
        return isVisible(to, file, receiver, from)
    }
}