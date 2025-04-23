// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix

internal object InapplicableJvmFieldFixFactories {

    val removeAnnotationFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InapplicableJvmField ->
        listOf(
            RemoveAnnotationFix(KotlinBundle.message("remove.jvmfield.annotation"), diagnostic.psi)
        )
    }
}
