// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.TooLongCharLiteralToStringFix
import org.jetbrains.kotlin.psi.KtConstantExpression

internal object TooLongCharLiteralToStringFixFactory {

    val illegalEscapeFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IllegalEscape ->
        val element = diagnostic.psi as? KtConstantExpression ?: return@ModCommandBased emptyList()
        TooLongCharLiteralToStringFix.createIfApplicable(element)?.let(::listOf) ?: emptyList()
    }

    val tooManyCharactersInCharacterLiteralFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TooManyCharactersInCharacterLiteral ->
        val element = diagnostic.psi as? KtConstantExpression ?: return@ModCommandBased emptyList()
        TooLongCharLiteralToStringFix.createIfApplicable(element)?.let(::listOf) ?: emptyList()
    }
}
