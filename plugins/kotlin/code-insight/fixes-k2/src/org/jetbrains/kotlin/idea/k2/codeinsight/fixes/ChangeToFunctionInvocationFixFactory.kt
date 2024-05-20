// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.ChangeToFunctionInvocationUtils
import org.jetbrains.kotlin.psi.KtExpression

internal object ChangeToFunctionInvocationFixFactory {

    val changeToFunctionInvocationFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.FunctionCallExpected ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()

        listOf(
            ChangeToFunctionInvocationFix(expression)
        )
    }

    private class ChangeToFunctionInvocationFix(
        element: KtExpression,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, Unit>(element, Unit) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtExpression,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            ChangeToFunctionInvocationUtils.applyTo(actionContext.project, element)
        }

        override fun getFamilyName(): String {
            return KotlinBundle.message("fix.change.to.function.invocation")
        }
    }
}