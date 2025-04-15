// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractInlineMethodCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.psi.KtCallExpression

class KotlinInlineMethodCompletionCommandProvider : AbstractInlineMethodCompletionCommandProvider() {
    override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
        var currentOffset = offset
        if (currentOffset == 0) return null
        var element = getCommandContext(offset, psiFile) ?: return null
        if (element is PsiWhiteSpace) {
            element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
        }
        currentOffset = element.textRange?.endOffset ?: currentOffset
        val callExpression = element.findParentOfType<KtCallExpression>() ?: return null
        val valueArgumentList = callExpression.valueArgumentList
        if (valueArgumentList != null && (callExpression.textRange.endOffset == currentOffset ||
                    valueArgumentList.textRange?.endOffset == currentOffset ||
                    valueArgumentList.textRange?.startOffset == currentOffset)
        ) {
            return valueArgumentList.textRange?.startOffset
        }
        return null
    }
}