// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty

object AddLateInitFactory {

    val addLateInitFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.MustBeInitializedOrBeAbstract ->
        val property: KtProperty = diagnostic.psi
        if (!property.isVar) return@IntentionBased emptyList()

        val type = property.getReturnKtType()

        if (type.isPrimitive || type.canBeNull) return@IntentionBased emptyList()

        listOf(AddModifierFix(property, KtTokens.LATEINIT_KEYWORD))
    }
}