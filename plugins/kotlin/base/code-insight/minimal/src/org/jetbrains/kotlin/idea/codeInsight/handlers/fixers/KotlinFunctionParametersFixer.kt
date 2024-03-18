// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.math.max


class KotlinFunctionParametersFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtNamedFunction) return

        val parameterList = psiElement.valueParameterList
        if (parameterList == null) {
            val identifier = psiElement.nameIdentifier ?: return

            // Insert () after name or after type parameters list when it placed after name
            val offset = max(identifier.range.end, psiElement.typeParameterList?.range?.end ?: psiElement.range.start)
            editor.document.insertString(offset, "()")
            processor.registerUnresolvedError(offset + 1)
        } else {
            val rParen = parameterList.lastChild ?: return

            if (")" != rParen.text) {
                val params = parameterList.parameters
                val offset = if (params.isEmpty()) parameterList.range.start + 1 else params.last().range.end
                editor.document.insertString(offset, ")")
            }
        }
    }
}
