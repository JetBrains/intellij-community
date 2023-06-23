// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic.ReturnInFunctionWithExpressionBody
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinDiagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.quickfix.ConvertToBlockBodyFix
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ConvertToBlockBodyFixFactory {
    val convertToBlockBodyFixFactory: KotlinDiagnosticFixFactory<ReturnInFunctionWithExpressionBody> =
        diagnosticFixFactory(ReturnInFunctionWithExpressionBody::class) { diagnostic ->
            val element = diagnostic.psi
            val declaration = element.getStrictParentOfType<KtDeclarationWithBody>()
                ?: return@diagnosticFixFactory emptyList()
            val context = ConvertToBlockBodyUtils.createContext(declaration, ::shortenReferences, reformat = false)
                ?: return@diagnosticFixFactory emptyList()
            listOf(ConvertToBlockBodyFix(declaration, context))
        }
}
