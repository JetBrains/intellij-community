// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal object DeclarationCantBeInlinedFixFactories {

    val removeOpenModifierFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.DeclarationCantBeInlined ->
        val function = diagnostic.psi as? KtNamedFunction ?: return@IntentionBased emptyList()
        val containingClass = function.containingClass() ?: return@IntentionBased emptyList()
        if (containingClass.isInterface()) return@IntentionBased emptyList()
        if (!function.hasModifier(KtTokens.OPEN_KEYWORD)) return@IntentionBased emptyList()

        listOf(
            RemoveModifierFixBase(function, KtTokens.OPEN_KEYWORD, false)
        )
    }
}
