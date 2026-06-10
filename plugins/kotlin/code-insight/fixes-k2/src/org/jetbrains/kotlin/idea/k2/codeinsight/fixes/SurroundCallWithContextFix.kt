// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class SurroundCallWithContextFix(
    element: KtExpression,
    private val wrapper: Wrapper,
    private val candidateName: String,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtExpression>(element) {

    enum class Wrapper(val keyword: String) {
        CONTEXT("context"), WITH("with")
    }

    override fun invoke(context: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)
        val newExpression = psiFactory.createExpression(
            "${wrapper.keyword}($candidateName) { ${element.text} }"
        )

        element.replace(newExpression)
    }

    override fun getActionPresentation(context: ActionContext, element: KtExpression): Presentation =
        Presentation.of(
                KotlinBundle.message("fix.surround.call.with.0.argument.1", wrapper.keyword, candidateName)
        )

    override fun getFamilyName(): String =
        KotlinBundle.message("fix.surround.call.with.context.family")
}
