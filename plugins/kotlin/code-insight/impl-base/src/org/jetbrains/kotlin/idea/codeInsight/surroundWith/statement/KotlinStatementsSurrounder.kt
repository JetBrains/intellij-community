// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtExpression

abstract class KotlinStatementsSurrounder : Surrounder {
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isApplicable(elements: Array<PsiElement>): Boolean {
        if (elements.isEmpty()) {
            return false
        }
        if (elements.size == 1 && elements[0] is KtExpression) {
            val expr = elements[0] as KtExpression
            if (!isApplicableWhenUsedAsExpression) {
                allowAnalysisOnEdt {
                    if (analyze(expr) { expr.isUsedAsExpression() }) {
                        return false
                    }
                }
            }
        }
        return true
    }

    protected open val isApplicableWhenUsedAsExpression: Boolean = true

    @Throws(IncorrectOperationException::class)
    override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        val container = elements[0].parent ?: return null
        return surroundStatements(project, editor, container, elements)
    }

    protected abstract fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<PsiElement>
    ): TextRange?
}
