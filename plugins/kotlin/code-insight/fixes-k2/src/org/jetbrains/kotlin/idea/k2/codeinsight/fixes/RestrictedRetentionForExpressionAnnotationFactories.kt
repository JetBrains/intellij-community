// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddSourceRetentionFix
import org.jetbrains.kotlin.idea.quickfix.ChangeRetentionToSourceFix
import org.jetbrains.kotlin.idea.quickfix.RemoveExpressionTargetFix
import org.jetbrains.kotlin.idea.quickfix.findExpressionTargetArgument
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal object RestrictedRetentionForExpressionAnnotationFactories {

    val quickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.RestrictedRetentionForExpressionAnnotationError ->
        val annotationEntry = diagnostic.psi as? KtAnnotationEntry ?: return@ModCommandBased emptyList()
        val containingClass = annotationEntry.containingClass() ?: return@ModCommandBased emptyList()
        val retentionAnnotation = findAnnotation(containingClass, StandardNames.FqNames.retention)
        val targetAnnotation = findAnnotation(containingClass, StandardNames.FqNames.target)
        val expressionTargetArgument = if (targetAnnotation != null) findExpressionTargetArgument(targetAnnotation) else null

        listOfNotNull(
            if (expressionTargetArgument != null) RemoveExpressionTargetFix(expressionTargetArgument) else null,
            if (retentionAnnotation == null) AddSourceRetentionFix(containingClass) else ChangeRetentionToSourceFix(retentionAnnotation)
        )
    }

    private fun KaSession.findAnnotation(ktClass: KtClass, fqName: FqName): KtAnnotationEntry? {
        return ktClass.annotationEntries.firstOrNull {
            it.typeReference?.text?.endsWith(fqName.shortName().asString()) == true
                    && it.typeReference?.type?.expandedSymbol?.importableFqName == fqName
        }
    }
}
