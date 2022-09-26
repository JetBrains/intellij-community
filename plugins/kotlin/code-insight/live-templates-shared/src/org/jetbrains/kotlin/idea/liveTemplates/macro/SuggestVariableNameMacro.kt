// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.*

abstract class AbstractSuggestVariableNameMacro : KotlinMacro() {
    override fun getName() = "kotlinSuggestVariableName"
    override fun getPresentableName() = "kotlinSuggestVariableName()"

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext): Result? {
        return findAppropriateNames(context).firstOrNull()?.let(::TextResult)
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext): Array<out LookupElement>? {
        return findAppropriateNames(context)
            .takeIf { it.size >= 2 }
            ?.map { LookupElementBuilder.create(it) }
            ?.toTypedArray()
    }

    private fun findAppropriateNames(context: ExpressionContext): Collection<String> {
        val project = context.project
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = context.editor?.document ?: return emptyList()
        documentManager.commitDocument(document)

        val psiFile = documentManager.getPsiFile(document) as? KtFile ?: return emptyList()
        val targetElement = psiFile.findElementAt(context.startOffset) ?: return emptyList()
        val targetDeclaration = targetElement.parent as? KtCallableDeclaration ?: return emptyList()

        if (targetElement != targetDeclaration.nameIdentifier) {
            return emptyList()
        }

        return suggestNames(targetDeclaration)
    }

    protected abstract fun suggestNames(declaration: KtCallableDeclaration): Collection<String>
}