// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

@ApiStatus.Internal
internal object DotQualifiedExpressionInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.commitDocument()
        val docManager = PsiDocumentManager.getInstance(context.project)
        val psiFile = docManager.getPsiFile(context.document) ?: return
        val element = psiFile.findElementAt(context.startOffset) ?: return
        // At this moment, IDEA already inserted the lookup string instead of the input part. The whole element should be replaced.
        // E.g., completing `libs.junit.ju<>` with `libs.junit.jupiter` will result in `libs.junit.libs.junit.jupiter`
        val elementToReplace = getTopmostDotQualifiedExpression(element) ?: return
        context.document.replaceString(
            elementToReplace.startOffset,
            elementToReplace.endOffset,
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