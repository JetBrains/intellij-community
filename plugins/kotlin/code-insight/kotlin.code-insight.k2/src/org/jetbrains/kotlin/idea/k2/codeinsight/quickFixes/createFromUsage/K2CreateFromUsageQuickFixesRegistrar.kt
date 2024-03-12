// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.psi.KtCallExpression

class K2CreateFromUsageQuickFixesRegistrar : KotlinQuickFixRegistrar() {

    private val createFunctionFromArgumentTypeMismatch =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.ArgumentTypeMismatch ->
            val psi = diagnostic.psi
            val callExpression = PsiTreeUtil.getParentOfType(psi, KtCallExpression::class.java)
            if (callExpression == null) {
                listOf()
            } else {
                buildRequestsAndActions(callExpression)
            }
        }

    override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(createFunctionFromArgumentTypeMismatch)
    }
}