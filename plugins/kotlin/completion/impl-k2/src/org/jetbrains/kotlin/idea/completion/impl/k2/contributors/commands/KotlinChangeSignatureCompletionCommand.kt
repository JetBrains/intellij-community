// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractChangeSignatureCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.ui.EDT
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

internal class KotlinChangeSignatureCompletionCommand : AbstractChangeSignatureCompletionCommandProvider() {
    override fun findChangeSignatureOffset(offset: Int, file: PsiFile): Int? {
        var currentOffset = offset
        if (currentOffset == 0) return null
        var element = getCommandContext(currentOffset, file) ?: return null
        if (element is PsiWhiteSpace) {
            element = PsiTreeUtil.prevVisibleLeaf(element) ?: return null
            currentOffset = element.textRange.startOffset
        }

        val callExpression = element.parentOfType<KtCallExpression>()
        if (callExpression != null &&
            (EDT.isCurrentThreadEdt() ||
                    analyze(callExpression) {
                        callExpression.referenceExpression()?.mainReference?.resolve()?.isWritable == true
                    })
        ) {
            if (callExpression.textRange.endOffset == currentOffset) {
                return callExpression.valueArgumentList?.textRange?.startOffset
            } else {
                return currentOffset
            }
        }
        val method = element.parentOfType<KtFunction>()
        if (method == null) return null
        if ((method.valueParameterList?.textRange?.endOffset ?: 0) >= currentOffset ||
            method.textRange?.endOffset == currentOffset
        ) return method.valueParameterList?.textRange?.endOffset
        return null
    }
}