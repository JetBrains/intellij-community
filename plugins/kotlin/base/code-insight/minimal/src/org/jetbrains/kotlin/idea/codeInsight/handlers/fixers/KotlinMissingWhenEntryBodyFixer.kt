// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtWhenEntry

class KotlinMissingWhenEntryBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is KtWhenEntry || element.expression != null) return
        val doc = editor.document
        val arrow = element.arrow
        if (arrow != null) {
            doc.insertString(arrow.range.end, "{\n}")
        } else {
            val lastCondition = element.conditions.lastOrNull() ?: return
            if (PsiTreeUtil.findChildOfType(lastCondition, PsiErrorElement::class.java) != null) return
            val offset = lastCondition.range.end
            val caretModel = editor.caretModel
            if (doc.getLineNumber(caretModel.offset) == doc.getLineNumber(element.range.start)) caretModel.moveToOffset(offset)
            doc.insertString(offset, "-> {\n}")
        }
    }
}
