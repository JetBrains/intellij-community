// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractChangeSignatureCompletionCommand
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.ui.EDT
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

internal class KotlinChangeSignatureCompletionCommand : AbstractChangeSignatureCompletionCommand() {
    override fun findChangeSignatureOffset(offset: Int, file: PsiFile): Int? {
        if (offset == 0) return null
        val element = getContext(offset, file) ?: return null
        val callExpression = element.parentOfType<KtCallExpression>()
        if (callExpression != null &&
            (EDT.isCurrentThreadEdt() ||
                    analyze(callExpression) {
                        callExpression.referenceExpression()?.mainReference?.resolve()?.isWritable == true
                    })
        ) {
            if (callExpression.textRange.endOffset == offset) {
                return callExpression.valueArgumentList?.textRange?.startOffset
            } else {
                return offset
            }
        }
        val method = element.parentOfType<KtNamedFunction>()
        if (method == null) return null
        if ((method.valueParameterList?.textRange?.endOffset ?: 0) >= offset) return offset
        if (method.textRange?.endOffset == offset) return method.valueParameterList?.textRange?.endOffset
        return null
    }
}