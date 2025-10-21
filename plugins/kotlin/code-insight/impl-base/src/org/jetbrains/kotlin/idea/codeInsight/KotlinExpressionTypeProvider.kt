// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.ide.util.EditorHelper
import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.utils.shouldShowType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

abstract class KotlinExpressionTypeProvider : ExpressionTypeProvider<KtExpression>() {
    override fun getExpressionsAt(elementAt: PsiElement): List<KtExpression> {
        val candidates = elementAt.parentsWithSelf.filterIsInstance<KtExpression>().filter { it.shouldShowType() }.toList()

        val selectionTextRange = EditorHelper.getMaybeInjectedEditor(elementAt)?.let { editor ->
            EditorUtil.getSelectionInAnyMode(editor)
        } ?: TextRange.EMPTY_RANGE
        val anchor =
            candidates.firstOrNull { selectionTextRange.isEmpty || it.textRange.contains(selectionTextRange) } ?: return emptyList()
        return candidates.filter { it.textRange.startOffset == anchor.textRange.startOffset }
    }
}