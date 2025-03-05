// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ReplaceWithDotCallFixFactory {
    val replaceWithDotCallFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.UnnecessarySafeCall> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnnecessarySafeCall ->
            // TODO Quite harsh implementation. Add logic from Fe10ReplaceWithDotCallFixFactory

            val qualifiedExpression = diagnostic.psi.getParentOfType<KtSafeQualifiedExpression>(strict = false)
                ?: return@ModCommandBased emptyList()

            listOf(
                ReplaceWithDotCallFix(qualifiedExpression),
            )
        }
}