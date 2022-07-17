// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.HintAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

internal open class ImportFix(expression: KtSimpleNameExpression) : AbstractImportFix(expression, MyFactory) {
    override fun elementsToCheckDiagnostics(): Collection<PsiElement> {
        val expression = element ?: return emptyList()
        return listOfNotNull(expression, expression.parent?.takeIf { it is KtCallExpression })
    }

    companion object MyFactory : Factory() {
        override fun createImportAction(diagnostic: Diagnostic): ImportFix? {
            val simpleNameExpression = when (val element = diagnostic.psiElement) {
                is KtSimpleNameExpression -> element
                is KtCallExpression -> element.calleeExpression
                else -> null
            } as? KtSimpleNameExpression ?: return null

            val hintsEnabled = AbstractImportFixInfo.isHintsEnabled(diagnostic.psiFile)
            return if (hintsEnabled) ImportFixWithHint(simpleNameExpression) else ImportFix(simpleNameExpression)
        }
    }
}

internal class ImportFixWithHint(expression: KtSimpleNameExpression): ImportFix(expression), HintAction {
    override fun fixSilently(editor: Editor): Boolean {
        if (isOutdated()) return false
        val element = element ?: return false
        val project = element.project
        val addImportAction = createActionWithAutoImportsFilter(project, editor, element)
        return if (addImportAction.isUnambiguous()) {
            addImportAction.execute()
            true
        } else false
    }
}
