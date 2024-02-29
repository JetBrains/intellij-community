// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.*
import org.jetbrains.kotlin.psi.KtCallExpression

class K2CreateFromUsageQuickFixesRegistrar : KotlinQuickFixRegistrar() {
    private val createFunctionFromArgumentTypeMismatch: KotlinDiagnosticFixFactory<KtFirDiagnostic.ArgumentTypeMismatch> =
        diagnosticFixFactoryFromIntentionActions(KtFirDiagnostic.ArgumentTypeMismatch::class) { diagnostic ->
            val psi = diagnostic.psi
            val callExpression = PsiTreeUtil.getParentOfType(psi, KtCallExpression::class.java)
            if (callExpression == null) {
                listOf()
            } else {
                buildRequestsAndActions(callExpression)
            }
        }

    override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(createFunctionFromArgumentTypeMismatch)
    }
}