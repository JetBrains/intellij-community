// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

class K2CreateFromUsageQuickFixesRegistrar : KotlinQuickFixRegistrar() {

    private val createFunctionFromArgumentTypeMismatch: KotlinQuickFixFactory.IntentionBased<KtFirDiagnostic.ArgumentTypeMismatch> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.ArgumentTypeMismatch ->
            val psi = diagnostic.psi
            val callExpression = PsiTreeUtil.getParentOfType(psi, KtCallExpression::class.java)
            if (callExpression == null) {
                listOf()
            } else {
                K2CreateFunctionFromUsageBuilder.buildRequestsAndActions(callExpression)
            }
        }
    private val createFunctionFromTooManyArguments: KotlinQuickFixFactory.IntentionBased<KtFirDiagnostic.TooManyArguments> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.TooManyArguments ->
            val psi = diagnostic.psi
            val callExpression = PsiTreeUtil.getParentOfType(psi, KtCallExpression::class.java)
            if (callExpression == null) {
                listOf()
            } else {
                K2CreateFunctionFromUsageBuilder.buildRequestsAndActions(callExpression)
            }
        }
    private val createFunctionFromMissingArguments: KotlinQuickFixFactory.IntentionBased<KtFirDiagnostic.NoValueForParameter> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.NoValueForParameter ->
            val psi = diagnostic.psi
            val expression = if (psi is KtQualifiedExpression) psi.selectorExpression else psi
            val callExpression = PsiTreeUtil.getParentOfType(expression, KtCallExpression::class.java, false)
            if (callExpression == null) {
                listOf()
            } else {
                K2CreateFunctionFromUsageBuilder.buildRequestsAndActions(callExpression)
            }
        }
    private val createVariableInsteadOfPackageReference: KotlinQuickFixFactory.IntentionBased<KtFirDiagnostic.ExpressionExpectedPackageFound> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.ExpressionExpectedPackageFound ->
            val psi = diagnostic.psi
            listOfNotNull(K2CreateLocalVariableFromUsageBuilder.generateCreateLocalVariableAction(psi))
        }

    override val list: KotlinQuickFixesList = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(createFunctionFromArgumentTypeMismatch)
        registerFactory(createFunctionFromTooManyArguments)
        registerFactory(createFunctionFromMissingArguments)
        registerFactory(createVariableInsteadOfPackageReference)
    }
}