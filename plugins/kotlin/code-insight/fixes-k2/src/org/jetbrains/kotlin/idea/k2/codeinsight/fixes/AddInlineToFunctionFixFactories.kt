// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object AddInlineToFunctionFixFactories {

    val illegalInlineParameterModifierFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IllegalInlineParameterModifier ->
            val function = diagnostic.psi.getParentOfType<KtFunction>(true) ?: return@ModCommandBased emptyList()
            if (function.isLocal) {
                return@ModCommandBased emptyList()
            }
            listOf(AddInlineToFunctionFix(function))
        }

    private class AddInlineToFunctionFix(element: KtFunction) : PsiUpdateModCommandAction<KtFunction>(element) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtFunction,
            updater: ModPsiUpdater,
        ) {
            element.addModifier(KtTokens.INLINE_KEYWORD)
        }

        override fun getPresentation(
            context: ActionContext,
            element: KtFunction,
        ): Presentation = Presentation.of(
            KotlinBundle.message("fix.add.modifier.inline.function.text", element.name.toString()),
        )

        override fun getFamilyName(): String =
            KotlinBundle.message("fix.add.modifier.inline.function.family")
    }
}
