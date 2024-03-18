// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

class KotlinValueArgumentListFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {

    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is KtValueArgumentList || element.rightParenthesis != null) return
        val lPar = element.leftParenthesis ?: return

        val lastArgument = element.arguments.lastOrNull()
        if (lastArgument != null && PsiUtilCore.hasErrorElementChild(lastArgument)) {
            val prev = lastArgument.getPrevSiblingIgnoringWhitespace() ?: lPar
            val offset = prev.endOffset
            if (prev == lPar) {
                editor.document.insertString(offset, ")")
                editor.caretModel.moveToOffset(offset)
            } else {
                editor.document.insertString(offset, " )")
                editor.caretModel.moveToOffset(offset + 1)
            }
        } else {
            val offset = lastArgument?.endOffset ?: element.endOffset
            editor.document.insertString(offset, ")")
            editor.caretModel.moveToOffset(offset + 1)
        }
    }

}