// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
abstract class AbstractAnonymousSuperMacro : KotlinMacro() {
    override fun getName() = "anonymousSuper"
    override fun getPresentableName() = "anonymousSuper()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val editor = context.editor
        if (editor != null) {
            AnonymousTemplateEditingListener.registerListener(editor, context.project)
        }

        val supertype = getSupertypes(params, context).firstOrNull() ?: return null
        return KotlinPsiElementResult(supertype)
    }

    override fun calculateLookupItems(params: Array<Expression>, context: ExpressionContext): Array<LookupElement>? {
        return getSupertypes(params, context)
            .takeIf { it.size >= 2 }
            ?.map { LookupElementBuilder.create(it) }
            ?.toTypedArray()
    }

    private fun getSupertypes(params: Array<Expression>, context: ExpressionContext): Collection<PsiNamedElement> {
        if (params.isNotEmpty()) {
            return emptyList()
        }

        val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
        psiDocumentManager.commitAllDocuments()

        val document = context.editor?.document ?: return emptyList()
        val file = psiDocumentManager.getPsiFile(document) as? KtFile ?: return emptyList()

        val targetElement = file.findElementAt(context.startOffset) ?: return emptyList()
        val targetExpression = targetElement.findParentOfType<KtExpression>() ?: return emptyList()
        return resolveSupertypes(targetExpression, file)
    }

    protected abstract fun resolveSupertypes(expression: KtExpression, file: KtFile): Collection<PsiNamedElement>
}