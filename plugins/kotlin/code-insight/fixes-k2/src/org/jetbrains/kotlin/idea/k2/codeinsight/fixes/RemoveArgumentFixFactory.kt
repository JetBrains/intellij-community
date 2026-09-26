// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveArgumentFix
import org.jetbrains.kotlin.psi.KtValueArgument

internal object RemoveArgumentFixFactory {

    val removeArgumentFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TooManyArguments ->
        val argument = diagnostic.psi.parent as? KtValueArgument ?: return@ModCommandBased emptyList()

        listOf(
            RemoveArgumentFix(argument)
        )
    }
}
