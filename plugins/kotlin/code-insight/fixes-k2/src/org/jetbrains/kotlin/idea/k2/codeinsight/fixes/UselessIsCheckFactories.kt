// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFix
import org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFixForWhen
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object UselessIsCheckFactories {
    val uselessIsCheckFactory =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UselessIsCheck ->
            val element = diagnostic.psi.takeIf { it.isWritable } ?: return@IntentionBased emptyList()
            val expression = element.getNonStrictParentOfType<KtIsExpression>() ?: return@IntentionBased emptyList()
            listOf(RemoveUselessIsCheckFix(expression, diagnostic.compileTimeCheckResult))
        }

    val uselessWhenCheckFactory =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UselessIsCheck ->
            val element = diagnostic.psi.takeIf { it.isWritable } ?: return@IntentionBased emptyList()
            val expression = element.getNonStrictParentOfType<KtWhenConditionIsPattern>() ?: return@IntentionBased emptyList()
            if (expression.getStrictParentOfType<KtWhenEntry>()?.guard != null) return@IntentionBased emptyList()
            listOf(RemoveUselessIsCheckFixForWhen(expression, diagnostic.compileTimeCheckResult))
        }

}
