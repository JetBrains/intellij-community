// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.handlers

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class WithExpressionPrefixInsertHandler(val prefix: String) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        item.handleInsert(context)

        postHandleInsert(context)
    }

    fun postHandleInsert(context: InsertionContext) {
        val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)

        val offset = context.startOffset
        val token = context.file.findElementAt(offset)!!
        var expression = token.getStrictParentOfType<KtExpression>() ?: return
        if (expression is KtSimpleNameExpression) {
            var parent = expression.getParent()
            if (parent is KtCallExpression && expression == parent.calleeExpression) {
                expression = parent
                parent = parent.parent
            }
            if (parent is KtDotQualifiedExpression && expression == parent.selectorExpression) {
                expression = parent
            }
        }

        context.document.insertString(expression.textRange.startOffset, prefix)
    }
}