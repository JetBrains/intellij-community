// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveFunctionBodyFix

internal object AbstractFunctionWithBodyFixFactory {

    val removeFunctionBody = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AbstractFunctionWithBody ->
        val function = diagnostic.psi
        if (!function.hasBody()) return@ModCommandBased emptyList()

        listOf(
            RemoveFunctionBodyFix(function)
        )
    }
}