// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal object AddNewLineAfterAnnotationsFixFactory {
    val addNewLineAfterAnnotationsFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.WrappedLhsInAssignmentWarning ->
        val annotatedExpression = when (val psi = diagnostic.psi) {
            is KtAnnotatedExpression -> psi
            is KtBinaryExpression -> psi.left as? KtAnnotatedExpression
            else -> null
        } ?: return@ModCommandBased emptyList()
        
        if (annotatedExpression.baseExpression == null) return@ModCommandBased emptyList()
        
        listOf(AddNewLineAfterAnnotationsFix(annotatedExpression))
    }
}