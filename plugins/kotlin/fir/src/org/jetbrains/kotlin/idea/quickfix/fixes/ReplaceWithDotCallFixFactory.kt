// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.idea.fir.api.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.frontend.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ReplaceWithDotCallFixFactory {
    val replaceWithDotCallFactory = diagnosticFixFactory(KtFirDiagnostic.UnnecessarySafeCall::class) { diagnostic ->
        // TODO Quite harsh implementation. Add logic from Fe10ReplaceWithDotCallFixFactory

        val qualifiedExpression = diagnostic.psi.getParentOfType<KtSafeQualifiedExpression>(strict = false)
            ?: return@diagnosticFixFactory emptyList()

        return@diagnosticFixFactory listOf(ReplaceWithDotCallFix(qualifiedExpression))
    }
}