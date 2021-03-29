// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.idea.fir.api.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.frontend.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.frontend.api.types.isPrimitive
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty

object AddLateInitFactory {
    val addLateInitFactory = diagnosticFixFactory<KtFirDiagnostic.MustBeInitializedOrBeAbstract> { diagnostic ->
        val property: KtProperty = diagnostic.psi
        if (!property.isVar) return@diagnosticFixFactory emptyList()

        val type = property.getReturnKtType()

        if (type.isPrimitive || type.canBeNull) return@diagnosticFixFactory emptyList()

        listOf(AddModifierFix(property, KtTokens.LATEINIT_KEYWORD))
    }
}