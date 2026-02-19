// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.WrongLongSuffixFix
import org.jetbrains.kotlin.psi.KtConstantExpression

internal object WrongLongSuffixFixFactory {

    val changeToCorrectLongSuffix = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.WrongLongSuffix ->
        val element = diagnostic.psi as? KtConstantExpression ?: return@ModCommandBased emptyList()
        listOf(WrongLongSuffixFix(element))
    }
}
