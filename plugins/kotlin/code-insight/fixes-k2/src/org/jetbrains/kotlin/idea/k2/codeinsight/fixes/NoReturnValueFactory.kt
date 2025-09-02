// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.createDeclarationByPattern

internal object NoReturnValueFactory {
    val noReturnValue =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnValueNotUsed ->
            createQuickFix(diagnostic.psi)
        }

    private fun createQuickFix(
        element: KtElement,
    ): List<UnderscoreValueFix> {
        return listOf(UnderscoreValueFix(element))
    }

    private class UnderscoreValueFix(
        element: KtElement,
    ) : PsiUpdateModCommandAction<KtElement>(element) {
        override fun getFamilyName(): String = KotlinBundle.message("explicitly.ignore.return.value")

        override fun invoke(
            context: ActionContext,
            element: KtElement,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(element.project)
            val variableDeclaration =
                factory.createDeclarationByPattern<KtVariableDeclaration>("val $0 = $1", "_", element.text)
            element.replace(variableDeclaration)
        }
    }
}