// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.annotations.ApiStatus

/*
 * Unlike the default insert handler, this implementation replaces the string inside quotes
 * including delimiters like ':' and '-', e.g., "my-group:my-artifact:version"
 */
@ApiStatus.Internal
object FullStringInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val result = item.getObject() as? String ?: return
        // IDEA was so kind to have replaced part of the initial string for us,
        // but we would like to replace the whole string
        context.commitDocument()
        val docManager = PsiDocumentManager.getInstance(context.project)
        val psiFile = docManager.getPsiFile(context.document)!!
        val element = psiFile.findElementAt(context.startOffset)!!

        context.document.replaceString(
          element.startOffset,
          element.endOffset,
          result
        )
    }
}