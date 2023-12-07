// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class KotlinCatchBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtCatchClause) return

        if (psiElement.catchBody == null) {
            editor.document.insertString(psiElement.endOffset, "{}")
        }
    }
}