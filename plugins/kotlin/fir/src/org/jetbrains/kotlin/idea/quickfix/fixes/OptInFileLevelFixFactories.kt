// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
import org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

internal object OptInFileLevelFixFactories {
    val optInFileLevelFixFactories =
        diagnosticFixFactories(
            KtFirDiagnostic.OptInUsage::class,
            KtFirDiagnostic.OptInUsageError::class,
            KtFirDiagnostic.OptInOverride::class,
            KtFirDiagnostic.OptInOverrideError::class
        ) { diagnostic ->
            val element = diagnostic.psi.findParentOfType<KtElement>() ?: return@diagnosticFixFactories emptyList()
            val optInMarkerClassId = OptInFixUtils.optInMarkerClassId(diagnostic) ?: return@diagnosticFixFactories emptyList()
            val optInFqName = OptInFixUtils.optInFqName() ?: return@diagnosticFixFactories emptyList()
            val containingFile = element.containingKtFile
            val annotationSymbol = OptInFixUtils.findAnnotation(optInMarkerClassId) ?: return@diagnosticFixFactories emptyList()

            if (!OptInFixUtils.annotationIsVisible(annotationSymbol, from = element)) return@diagnosticFixFactories emptyList()

            return@diagnosticFixFactories listOf(
                UseOptInFileAnnotationFix(
                    containingFile, optInFqName, optInMarkerClassId.asSingleFqName(),
                    findFileAnnotation(containingFile, optInFqName)?.createSmartPointer()
                )
            )
        }

    // Find the existing file-level annotation of the specified class if it exists
    context (KtAnalysisSession)
    private fun findFileAnnotation(file: KtFile, optInFqName: FqName): KtAnnotationEntry? =
        file.fileAnnotationList?.findAnnotation(ClassId.topLevel(optInFqName))
}