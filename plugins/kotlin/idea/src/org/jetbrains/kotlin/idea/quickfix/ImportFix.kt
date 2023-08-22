// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

internal class ImportFix(expression: KtSimpleNameExpression) : AbstractImportFix(expression, MyFactory) {
    override fun elementsToCheckDiagnostics(): Collection<PsiElement> {
        val expression = element ?: return emptyList()
        return listOfNotNull(expression, expression.parent?.takeIf { it is KtCallExpression })
    }

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        override fun areActionsAvailable(diagnostic: Diagnostic): Boolean {
            val expression = expression(diagnostic)
            return expression != null && expression.references.isNotEmpty()
        }

        override fun createImportAction(diagnostic: Diagnostic): ImportFix? =
            expression(diagnostic)?.let(::ImportFix)

        private fun expression(diagnostic: Diagnostic): KtSimpleNameExpression? =
            when (val element = diagnostic.psiElement) {
                is KtSimpleNameExpression -> element
                is KtCallExpression -> element.calleeExpression
                else -> null
            } as? KtSimpleNameExpression
    }
}
