// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty

internal object AddLateInitFactory {

    val addLateInitFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeAbstract ->
        val property: KtProperty = diagnostic.psi
        if (!property.isVar) return@ModCommandBased emptyList()

        val type = property.returnType

        if (type.isPrimitive || type.isNullable) return@ModCommandBased emptyList()

        listOf(AddModifierFix(property, KtTokens.LATEINIT_KEYWORD))
    }
}