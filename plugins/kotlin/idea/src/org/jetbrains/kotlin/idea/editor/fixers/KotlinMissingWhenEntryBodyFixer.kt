// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.editor.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtWhenEntry

class KotlinMissingWhenEntryBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is KtWhenEntry || element.expression != null) return
        val arrow = element.arrow
        if (arrow != null) {
            editor.document.insertString(arrow.range.end, "{\n}")
        } else {
            val lastCondition = element.conditions.lastOrNull() ?: return
            if (PsiTreeUtil.findChildOfType(lastCondition, PsiErrorElement::class.java) != null) return
            editor.document.insertString(lastCondition.range.end, "-> {\n}")
        }
    }
}
