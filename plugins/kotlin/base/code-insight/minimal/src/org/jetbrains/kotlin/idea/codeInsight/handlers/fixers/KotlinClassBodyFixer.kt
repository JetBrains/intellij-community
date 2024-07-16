// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.end
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

open class KotlinClassBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtClassOrObject) return

        val body = psiElement.body
        if (!body?.text.isNullOrBlank()) return

        var endOffset = psiElement.range.end

        if (body != null) {
            body.getPrevSiblingIgnoringWhitespaceAndComments()?.let {
                endOffset = it.endOffset
            }
        }

        endOffset = fixSuperTypeInitializer(psiElement, editor, endOffset)

        editor.caretModel.moveToOffset(endOffset - 1)

        // Insert '\n' to force a multiline body, otherwise there will be an empty body on one line and a caret on the next one.
        editor.document.insertString(endOffset, "{\n}")
    }

    protected open fun fixSuperTypeInitializer(
        psiElement: KtClassOrObject,
        editor: Editor,
        endOffset: Int
    ): Int {
        return endOffset
    }
}