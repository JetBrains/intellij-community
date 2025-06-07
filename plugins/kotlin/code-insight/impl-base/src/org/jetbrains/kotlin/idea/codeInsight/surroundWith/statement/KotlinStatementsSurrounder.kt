// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement

import com.intellij.lang.surroundWith.ModCommandSurrounder
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtExpression

abstract class KotlinStatementsSurrounder : ModCommandSurrounder() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    final override fun isApplicable(elements: Array<PsiElement>): Boolean {
        if (elements.isEmpty()) {
            return false
        }
        if (elements.size == 1 && elements[0] is KtExpression) {
            val expr = elements[0] as KtExpression
            if (!isApplicableWhenUsedAsExpression) {
                allowAnalysisOnEdt {
                    if (analyze(expr) { expr.isUsedAsExpression }) {
                        return false
                    }
                }
            }
        }
        return true
    }

    protected open val isApplicableWhenUsedAsExpression: Boolean = true

    @Throws(IncorrectOperationException::class)
    final override fun surroundElements(context: ActionContext, elements: Array<out PsiElement>): ModCommand {
        val container = elements[0].parent ?: return ModCommand.nop()
        return ModCommand.psiUpdate(context) { updater ->
            surroundStatements(
                context,
                updater.getWritable(container),
                elements.map { updater.getWritable(it) }.toTypedArray(),
                updater
            )
        }
    }

    protected abstract fun surroundStatements(
        context: ActionContext,
        container: PsiElement,
        statements: Array<PsiElement>,
        updater: ModPsiUpdater
    )
}
