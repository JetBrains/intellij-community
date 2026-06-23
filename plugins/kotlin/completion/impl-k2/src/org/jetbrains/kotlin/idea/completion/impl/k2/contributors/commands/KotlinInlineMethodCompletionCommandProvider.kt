// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractInlineMethodCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import com.intellij.util.ui.EDT
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

internal class KotlinInlineMethodCompletionCommandProvider : AbstractInlineMethodCompletionCommandProvider() {
    override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
        var currentOffset = offset
        if (currentOffset == 0) return null
        var element = getCommandContext(offset, psiFile) ?: return null
        if (element is PsiWhiteSpace) {
            element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
        }
        currentOffset = element.textRange?.endOffset ?: currentOffset
        if (!element.isWritable) return null
        val parent = element.parentOfType<KtNamedFunction>()
        if (parent != null && parent.nameIdentifier?.textRange?.endOffset == currentOffset) {
            return if (isNamedFunctionInlinable(parent)) currentOffset else null
        }
        if (parent != null &&
            parent.textRange.endOffset == currentOffset
        ) return if (isNamedFunctionInlinable(parent)) parent.nameIdentifier?.textRange?.endOffset else null

        val callExpression = element.findParentOfType<KtCallExpression>() ?: return null
        val valueArgumentList = callExpression.valueArgumentList
        if (valueArgumentList != null && (callExpression.textRange.endOffset == currentOffset ||
                    valueArgumentList.textRange?.endOffset == currentOffset ||
                    valueArgumentList.textRange?.startOffset == currentOffset)
        ) {
            if (!isCallInlinable(callExpression)) return null
            return valueArgumentList.textRange?.startOffset
        }
        return null
    }
}

private fun isNamedFunctionInlinable(function: KtNamedFunction): Boolean {
    if (function.nameIdentifier == null) return false
    if (function.containingKtFile.isCompiled) return false
    if (!function.hasBody()) return false
    return true
}

private fun isCallInlinable(callExpression: KtCallExpression): Boolean {
    if (EDT.isCurrentThreadEdt()) return true
    val resolved = analyze(callExpression) { callExpression.referenceExpression()?.mainReference?.resolve() }
    val function = resolved as? KtNamedFunction ?: return true
    return isNamedFunctionInlinable(function)
}