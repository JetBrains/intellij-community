// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddModuleOptInFix
import org.jetbrains.kotlin.idea.quickfix.UseOptInFileAnnotationFix
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

internal object OptInFileLevelFixFactories {

    val optInUsageFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInUsage ->
        createQuickFix(diagnostic)
    }

    val optInUsageErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInUsageError ->
        createQuickFix(diagnostic)
    }

    val optInUsageInheritanceFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInToInheritance ->
        createQuickFix(diagnostic)
    }

    val optInUsageInheritanceErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInToInheritanceError ->
        createQuickFix(diagnostic)
    }

    val optInOverrideFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInOverride ->
        createQuickFix(diagnostic)
    }

    val optInOverrideErrorFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInOverrideError ->
        createQuickFix(diagnostic)
    }

    private fun KaSession.createQuickFix(
        diagnostic: KaFirDiagnostic<PsiElement>,
    ): List<IntentionAction> {
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

        val result = mutableListOf<IntentionAction>()
        val argumentClassFqName = optInMarkerClassId.asSingleFqName()
        result += UseOptInFileAnnotationFix(
            element = containingFile,
            optInFqName = optInFqName,
            annotationFinder = { file: KtFile, annotationFqName: FqName -> findFileAnnotation(file, annotationFqName) },
            argumentClassFqName = argumentClassFqName,
        ).asIntention()

        containingFile.module?.let { module ->
            result += AddModuleOptInFix(
                file = containingFile,
                module = module,
                annotationFqName = argumentClassFqName,
            )
        }

        return result
    }

    // Find the existing file-level annotation of the specified class if it exists
    context (KaSession)
    private fun findFileAnnotation(file: KtFile, optInFqName: FqName): KtAnnotationEntry? =
        file.fileAnnotationList?.findAnnotation(ClassId.topLevel(optInFqName))
}