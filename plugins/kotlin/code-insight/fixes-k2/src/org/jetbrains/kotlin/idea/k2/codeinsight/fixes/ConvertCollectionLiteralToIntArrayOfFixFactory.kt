// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertCollectionLiteralToIntArrayOfFix

internal object ConvertCollectionLiteralToIntArrayOfFixFactory {

    private fun createFix(diagnostic: KaFirDiagnostic<*>): List<ModCommandAction> {
        return listOfNotNull(
            ConvertCollectionLiteralToIntArrayOfFix.createIfApplicable(element = diagnostic.psi)
        )
    }

    val convertCollectionLiteralToIntArrayOfErrorFixFactory
        = KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnsupportedArrayLiteralOutsideOfAnnotationError> { createFix(it) }

    val convertCollectionLiteralToIntArrayOfWarningFixFactory
        = KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnsupportedArrayLiteralOutsideOfAnnotationWarning> { createFix(it) }
}
