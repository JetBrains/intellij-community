// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

fun setupEditorSelection(editor: Editor, declaration: KtNamedDeclaration) {
    val caretModel = editor.caretModel
    val selectionModel = editor.selectionModel

    val offset = when (declaration) {
        is KtPrimaryConstructor -> declaration.getConstructorKeyword()?.endOffset ?: declaration.valueParameterList?.startOffset
        is KtSecondaryConstructor -> declaration.getConstructorKeyword().endOffset
        else -> declaration.nameIdentifier?.endOffset
    }
    if (offset != null) {
        caretModel.moveToOffset(offset)
    }

    fun positionBetween(left: PsiElement, right: PsiElement) {
        val from = left.siblings(withItself = false, forward = true).firstOrNull { it !is PsiWhiteSpace } ?: return
        val to = right.siblings(withItself = false, forward = false).firstOrNull { it !is PsiWhiteSpace } ?: return
        val startOffset = from.startOffset
        val endOffset = to.endOffset
        caretModel.moveToOffset(endOffset)
        selectionModel.setSelection(startOffset, endOffset)
    }

    when (declaration) {
        is KtNamedFunction, is KtSecondaryConstructor -> {
            (declaration as KtFunction).bodyBlockExpression?.let {
                positionBetween(it.lBrace!!, it.rBrace!!)
            }
        }
        is KtClassOrObject -> {
            caretModel.moveToOffset(declaration.nameIdentifier?.startOffset ?: declaration.startOffset)
        }
        is KtProperty -> {
            caretModel.moveToOffset(declaration.endOffset)
        }
    }
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
}
