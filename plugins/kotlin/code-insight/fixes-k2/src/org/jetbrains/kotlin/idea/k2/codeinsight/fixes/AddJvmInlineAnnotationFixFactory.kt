// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddJvmInlineAnnotationFix
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object AddJvmInlineAnnotationFixFactory {

    val addJvmInlineAnnotationFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ValueClassWithoutJvmInlineAnnotation ->
        val klass = diagnostic.psi.getParentOfType<KtClass>(strict = true) ?: return@ModCommandBased emptyList()

        listOf(
            AddJvmInlineAnnotationFix(klass)
        )
    }
}
