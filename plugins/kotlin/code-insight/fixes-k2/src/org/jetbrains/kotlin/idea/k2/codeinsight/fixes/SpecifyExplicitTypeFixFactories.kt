// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.SpecifyExplicitTypeQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.getTypeInfo
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration

object SpecifyExplicitTypeFixFactories {
    val ambiguousAnonymousTypeInferred: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.AmbiguousAnonymousTypeInferred> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AmbiguousAnonymousTypeInferred ->
            createQuickFix(diagnostic.psi)
        }

    val noExplicitReturnTypeInApiMode: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NoExplicitReturnTypeInApiMode> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoExplicitReturnTypeInApiMode ->
            createQuickFix(diagnostic.psi)
        }

    val noExplicitReturnTypeInApiModeWarning: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NoExplicitReturnTypeInApiModeWarning> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoExplicitReturnTypeInApiModeWarning ->
            createQuickFix(diagnostic.psi)
        }

    private fun KaSession.createQuickFix(declaration: KtDeclaration): List<SpecifyExplicitTypeQuickFix> =
        if (declaration is KtCallableDeclaration) listOf(SpecifyExplicitTypeQuickFix(declaration, getTypeInfo(declaration)))
        else emptyList()
}