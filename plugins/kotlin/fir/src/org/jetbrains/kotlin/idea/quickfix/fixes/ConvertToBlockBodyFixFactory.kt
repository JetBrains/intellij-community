// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.quickfix.ConvertToBlockBodyFix
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ConvertToBlockBodyFixFactory {
    val convertToBlockBodyFixFactory = diagnosticFixFactory(
        KtFirDiagnostic.ReturnInFunctionWithExpressionBody::class,
    ) { diagnostic ->
        val element = diagnostic.psi
        val declaration = element.getStrictParentOfType<KtDeclarationWithBody>()
            ?: return@diagnosticFixFactory emptyList()
        val context = ConvertToBlockBodyUtils.createContext(declaration, ::shortenReferences, reformat = false)
            ?: return@diagnosticFixFactory emptyList()
        listOf(ConvertToBlockBodyFix(declaration, context))
    }
}
