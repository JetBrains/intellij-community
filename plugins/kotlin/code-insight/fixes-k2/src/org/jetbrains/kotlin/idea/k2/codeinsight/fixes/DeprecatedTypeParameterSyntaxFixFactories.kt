// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.MigrateTypeParameterListFix
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object DeprecatedTypeParameterSyntaxFixFactories {

    val migrateTypeParameterListFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.DeprecatedTypeParameterSyntax ->
        val element = (diagnostic.psi as? KtNamedFunction)?.typeParameterList ?: return@ModCommandBased emptyList()

        listOf(
            MigrateTypeParameterListFix(element)
        )
    }
}
