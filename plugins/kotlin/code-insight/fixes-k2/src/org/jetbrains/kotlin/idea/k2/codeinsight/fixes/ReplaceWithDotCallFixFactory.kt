// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ReplaceWithDotCallFixFactory {
    val replaceWithDotCallFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.UnnecessarySafeCall ->
        // TODO Quite harsh implementation. Add logic from Fe10ReplaceWithDotCallFixFactory

        val qualifiedExpression = diagnostic.psi.getParentOfType<KtSafeQualifiedExpression>(strict = false)
            ?: return@IntentionBased emptyList()

        listOf(
            ReplaceWithDotCallFix(qualifiedExpression),
        )
    }
}