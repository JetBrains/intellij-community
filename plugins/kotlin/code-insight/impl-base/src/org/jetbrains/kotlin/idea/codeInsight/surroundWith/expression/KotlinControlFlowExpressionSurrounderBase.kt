// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

abstract class KotlinControlFlowExpressionSurrounderBase : KotlinExpressionSurrounder() {
    override val isApplicableToStatements: Boolean
        get() = false

    override fun surroundExpression(project: Project, editor: Editor, expression: KtExpression): TextRange? {
        val psiFactory = KtPsiFactory(project)

        val newElement = psiFactory.createExpressionByPattern(getPattern(), expression.text)
        val replaced = expression.replaced(newElement)

        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(replaced)

        return getRange(editor, replaced)
    }

    protected abstract fun getPattern(): String
    protected abstract fun getRange(editor: Editor, replaced: KtExpression): TextRange?
}