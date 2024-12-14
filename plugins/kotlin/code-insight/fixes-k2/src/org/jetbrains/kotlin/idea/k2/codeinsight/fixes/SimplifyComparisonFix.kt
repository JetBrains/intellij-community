// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SenselessComparison
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.SimplifyComparisonFix
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object SimplifyComparisonFixFactory {

    val simplifyComparisonFixFactory: KotlinQuickFixFactory.ModCommandBased<SenselessComparison> = KotlinQuickFixFactory.ModCommandBased { diagnostic: SenselessComparison ->
        val expression = diagnostic.psi.takeIf { it.getStrictParentOfType<KtDeclarationWithBody>() != null }
            ?: return@ModCommandBased emptyList()
        val compareResult = diagnostic.compareResult

        listOf(SimplifyComparisonFix(expression, compareResult))
    }
    
}
