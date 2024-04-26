// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinTypeSelectioner : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean {
        return e is KtTypeReference
                && e.getStrictParentOfType<KtObjectDeclaration>() == null
                && e.getStrictParentOfType<KtParameter>() == null
    }

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        if (e !is KtTypeReference) return null
        val callableDeclaration = e.parent as? KtCallableDeclaration ?: return null
        if (e != callableDeclaration.typeReference) return null
        val colonPosition = callableDeclaration.colon?.startOffset ?: return null
        return listOf(TextRange(colonPosition, e.endOffset))
    }
}
