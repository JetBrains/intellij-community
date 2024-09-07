// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal object ConfusingBranchConditionErrorFixFactories {

    val wrapExpressionInParenthesesFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ConfusingBranchConditionError ->
        val element = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        listOf(WrapExpressionInParenthesesFixFactory(element))
    }

    private class WrapExpressionInParenthesesFixFactory(
        element: KtExpression,
    ) : PsiUpdateModCommandAction<KtExpression>(element) {

        override fun getFamilyName(): String = KotlinBundle.message("wrap.expression.in.parentheses")

        override fun invoke(
            context: ActionContext,
            element: KtExpression,
            updater: ModPsiUpdater,
        ) {
            val wrapped = KtPsiFactory(context.project).createExpressionByPattern("($0)", element)
            element.replace(wrapped)
        }
    }
}
