// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils.createContext
import org.jetbrains.kotlin.idea.quickfix.ConvertToBlockBodyFix
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ConvertToBlockBodyFixFactory {

    val convertToBlockBodyFixFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.ReturnInFunctionWithExpressionBody> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnInFunctionWithExpressionBody ->
            val element = diagnostic.psi
            val declaration = element.getStrictParentOfType<KtDeclarationWithBody>()
                ?: return@ModCommandBased emptyList()

            val context = createContext(declaration, reformat = false)
                ?: return@ModCommandBased emptyList()

            listOf(ConvertToBlockBodyFix(declaration, context))
        }
}
