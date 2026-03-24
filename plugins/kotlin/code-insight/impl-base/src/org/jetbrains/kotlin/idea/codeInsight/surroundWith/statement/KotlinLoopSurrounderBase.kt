// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.addStatementsInBlock
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class KotlinLoopSurrounderBase<T: KtLoopExpression> : KotlinStatementsSurrounder() {

    override val isApplicableWhenUsedAsExpression: Boolean = false

    protected abstract val codeTemplate: String

    protected abstract fun getSelectionElement(loop: T): KtExpression?

    /**
     * Function to surround statements with a loop structure.
     */
    override fun surroundStatements(
        context: ActionContext,
        container: PsiElement,
        statements: Array<PsiElement>,
        updater: ModPsiUpdater
    ) {
        val factory = KtPsiFactory(context.project)
        val loopExpression = factory.createExpression(codeTemplate) as KtLoopExpression

        (loopExpression.body as? KtBlockExpression)?.let { body ->
            addStatementsInBlock(body, arrayOf(*statements))
        }

        val firstStatement = statements.first()
        @Suppress("UNCHECKED_CAST")
        val insertedLoop = container.addBefore(loopExpression, firstStatement) as T
        container.deleteChildRange(firstStatement, statements.last())

        val selectedElement = getSelectionElement(insertedLoop) ?: return
        updater.select(TextRange.from(selectedElement.textRange.startOffset, selectedElement.textLength))
    }
}
