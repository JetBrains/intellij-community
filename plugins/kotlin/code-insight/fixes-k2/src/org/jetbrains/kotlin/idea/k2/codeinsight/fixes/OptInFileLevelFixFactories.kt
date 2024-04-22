// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal object OptInFileLevelFixFactories {

    val optInUsageFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.OptInUsage ->
        createQuickFix(diagnostic)
    }

    val optInUsageErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.OptInUsageError ->
        createQuickFix(diagnostic)
    }

    val optInOverrideFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.OptInOverride ->
        createQuickFix(diagnostic)
    }

    val optInOverrideErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.OptInOverrideError ->
        createQuickFix(diagnostic)
    }

    context(KtAnalysisSession)
    private fun createQuickFix(
        diagnostic: KtFirDiagnostic<PsiElement>,
    ): List<UseOptInFileAnnotationFix> {
        val element = diagnostic.psi.findParentOfType<KtElement>()
            ?: return emptyList()

        val optInMarkerClassId = OptInFixUtils.optInMarkerClassId(diagnostic)
            ?: return emptyList()

        val optInFqName = OptInFixUtils.optInFqName()
            ?: return emptyList()

        val containingFile = element.containingKtFile
        val annotationSymbol = OptInFixUtils.findAnnotation(optInMarkerClassId)
            ?: return emptyList()

        if (!OptInFixUtils.annotationIsVisible(annotationSymbol, from = element)) {
            return emptyList()
        }

        return listOf(
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