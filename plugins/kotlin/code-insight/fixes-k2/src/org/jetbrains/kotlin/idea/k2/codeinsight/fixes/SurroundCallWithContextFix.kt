// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset

internal class SurroundCallWithContextFix(element: KtCallElement, private val wrapper: Wrapper) :
    KotlinPsiUpdateModCommandAction.ElementContextless<KtCallElement>(element) {

    enum class Wrapper(val keyword: String) {
        CONTEXT("context"), WITH("with")
    }

    override fun invoke(context: ActionContext, element: KtCallElement, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)

        val newExpression = psiFactory.createExpression("${wrapper.keyword}() { ${element.text} }")

        val replaced = element.replace(newExpression) as? KtCallExpression ?: return

        val argumentList = replaced.valueArgumentList?.leftParenthesis ?: return
        updater.moveCaretTo(argumentList.endOffset)
    }

    override fun getActionPresentation(context: ActionContext, element: KtCallElement): Presentation =
        Presentation.of(KotlinBundle.message("fix.surround.call.with.0", wrapper.keyword))

    override fun getFamilyName(): String =
        KotlinBundle.message("fix.surround.call.with.context.family")
}
