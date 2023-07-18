// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
import org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.checkers.OptInNames

object OptInFileLevelFixesFactories {
    val optInFileLevelFixesFactories =
        diagnosticFixFactories(
            KtFirDiagnostic.OptInUsage::class,
            KtFirDiagnostic.OptInUsageError::class,
            KtFirDiagnostic.OptInOverride::class,
            KtFirDiagnostic.OptInOverrideError::class
        ) { diagnostic ->
            val element = diagnostic.psi.findParentOfType<KtElement>() ?: return@diagnosticFixFactories emptyList()
            val optInMarkerFqName = optInMarkerFqName(diagnostic) ?: return@diagnosticFixFactories emptyList()
            val optInFqName = optInFqName() ?: return@diagnosticFixFactories emptyList()
            val containingFile = element.containingKtFile

            return@diagnosticFixFactories listOf(
                UseOptInFileAnnotationFix(
                    containingFile, optInFqName, optInMarkerFqName,
                    findFileAnnotation(containingFile, optInFqName)?.createSmartPointer()
                )
            )
        }

    // Find the existing file-level annotation of the specified class if it exists
    context (KtAnalysisSession)
    private fun findFileAnnotation(file: KtFile, optInFqName: FqName): KtAnnotationEntry? =
        file.fileAnnotationList?.findAnnotation(ClassId.topLevel(optInFqName))

    private fun optInMarkerFqName(diagnostic: KtFirDiagnostic<PsiElement>) = when (diagnostic) {
        is KtFirDiagnostic.OptInUsage -> diagnostic.optInMarkerFqName
        is KtFirDiagnostic.OptInUsageError -> diagnostic.optInMarkerFqName
        is KtFirDiagnostic.OptInOverride -> diagnostic.optInMarkerFqName
        is KtFirDiagnostic.OptInOverrideError -> diagnostic.optInMarkerFqName
        else -> null
    }

    context (KtAnalysisSession)
    private fun optInFqName(): FqName? = OptInNames.OPT_IN_FQ_NAME.takeIf { it.annotationApplicable() }
        ?: FqNames.OptInFqNames.OLD_USE_EXPERIMENTAL_FQ_NAME.takeIf { it.annotationApplicable() }

    context (KtAnalysisSession)
    private fun FqName.annotationApplicable() = getClassOrObjectSymbolByClassId(ClassId.topLevel(this)) != null

}