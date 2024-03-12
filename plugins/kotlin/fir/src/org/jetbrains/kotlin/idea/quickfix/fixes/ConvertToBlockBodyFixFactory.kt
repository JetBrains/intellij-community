// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils.createContext
import org.jetbrains.kotlin.idea.quickfix.ConvertToBlockBodyFix
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object ConvertToBlockBodyFixFactory {

    val convertToBlockBodyFixFactory =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.ReturnInFunctionWithExpressionBody ->
            val element = diagnostic.psi
            val declaration = element.getStrictParentOfType<KtDeclarationWithBody>()
                ?: return@IntentionBased emptyList()

            val context = createContext(declaration, ShortenReferencesFacility.getInstance(), reformat = false)
                ?: return@IntentionBased emptyList()

            listOf(ConvertToBlockBodyFix(declaration, context))
        }
}
