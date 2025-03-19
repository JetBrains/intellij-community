// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddJvmStaticAnnotationFix
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal object SubclassCantCallCompanionProtectedNonStaticFixFactory {

    val addJvmStaticAnnotation = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SubclassCantCallCompanionProtectedNonStatic ->
        val nameReference = diagnostic.psi as? KtNameReferenceExpression ?: return@ModCommandBased emptyList()

        listOfNotNull(
            AddJvmStaticAnnotationFix.createIfApplicable(nameReference)
        )
    }
}
