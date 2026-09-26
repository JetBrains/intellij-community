// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
import org.jetbrains.kotlin.name.ClassId

internal object OverridingIgnorableWithMustUseFixFactories {
    val addIgnorableReturnValueAnnotationFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.OverridingIgnorableWithMustUse ->
            listOf(
                AddAnnotationFix(diagnostic.psi, ClassId.fromString("kotlin.IgnorableReturnValue"))
            )
        }
}
