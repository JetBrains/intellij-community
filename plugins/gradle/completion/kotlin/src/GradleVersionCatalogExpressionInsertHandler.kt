// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/** Unlike the default insert handler, removes the rest of the KtDotQualifiedExpression after the <caret>.
 * For example, completing `libs.jun<caret>it.ju` with `libs.junit.jupiter` results in:
 * - `libs.junit.jupiter<caret>.ju` for the default handler (keeps `.ju`).
 * - `libs.junit.jupiter<caret>` for this handler.
 */
@ApiStatus.Internal
internal object GradleVersionCatalogExpressionInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.commitDocument()
        val docManager = PsiDocumentManager.getInstance(context.project)
        val psiFile = docManager.getPsiFile(context.document) ?: return
        val element = psiFile.findElementAt(context.startOffset) ?: return
        val wholeDotExpression = getTopmostDotQualifiedExpression(element) ?: return
        val endOffset = wholeDotExpression.endOffset
        context.document.replaceString(
            context.startOffset,
            endOffset,
            item.lookupString
        )
    }
}

private fun getTopmostDotQualifiedExpression(element: PsiElement): KtDotQualifiedExpression? {
    var result = element.parentOfType<KtDotQualifiedExpression>(withSelf = true) ?: return null
    while (result.parent is KtDotQualifiedExpression) {
        result = result.parent as KtDotQualifiedExpression
    }
    return result
}