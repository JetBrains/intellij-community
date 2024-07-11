// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix
import org.jetbrains.kotlin.psi.KtCallExpression

internal object AddEmptyArgumentListFixFactory {

    val addEmptyArgumentListFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ModifierFormForNonBuiltInSuspend ->
        listOfNotNull(
            (diagnostic.psi as? KtCallExpression)?.let { AddEmptyArgumentListFix(it) }
        )
    }
}
