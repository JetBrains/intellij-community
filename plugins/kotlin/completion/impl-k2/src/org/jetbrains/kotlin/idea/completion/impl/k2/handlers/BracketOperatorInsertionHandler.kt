// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiWhiteSpace
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@Serializable
internal object BracketOperatorInsertionHandler : SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val elementAtIndex = context.file.findElementAt(context.startOffset) ?: return
        val bracketParent = elementAtIndex.parent ?: return
        val dot = bracketParent.getPrevSiblingIgnoringWhitespaceAndComments()?.takeIf { it.text == "." } ?: return
        // Delete whitespaces between identifier, dot and brackets because
        // the bracket operator should never have a space preceding it.
        dot.siblings(forward = true, withItself = false).takeWhile { it is PsiWhiteSpace }.forEach { it.delete() }
        dot.siblings(forward = false, withItself = false).takeWhile { it is PsiWhiteSpace }.forEach { it.delete() }
        // In case there is a comment between dot and bracket, we delete whitespace from both sides
        bracketParent.siblings(forward = false).takeWhile { it is PsiWhiteSpace }.forEach { it.delete() }
        dot.delete()

        context.editor.caretModel.moveToOffset(elementAtIndex.startOffset + 1)
    }
}