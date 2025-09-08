// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractExtractLocalVariableCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractMethodCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractParameterCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

internal class KotlinExtractParameterCompletionCommandProvider : AbstractExtractParameterCompletionCommandProvider() {
    override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
        return findOffsetForLocalVariable(offset, psiFile)
    }
}

internal class KotlinExtractLocalVariableCompletionCommandProvider : AbstractExtractLocalVariableCompletionCommandProvider() {
    override fun findOutermostExpression(
        offset: Int,
        psiFile: PsiFile,
        editor: Editor?
    ): KtExpression? {
        return findExpressionInsideMethod(offset, psiFile)
    }
}

internal class KotlinExtractMethodCompletionCommandProvider : AbstractExtractMethodCompletionCommandProvider(
    actionId = "ExtractFunction",
    presentableName = KotlinBundle.message("action.ExtractFunction.text"),
    previewText = KotlinBundle.message("action.ExtractFunction.command.completion.description"),
) {
    override fun findOutermostExpression(
        offset: Int,
        psiFile: PsiFile,
        editor: Editor?
    ): PsiElement? {
        return findExpressionInsideMethod(offset, psiFile)
    }
}

private fun findExpressionInsideMethod(offset: Int, psiFile: PsiFile): KtExpression? {
    val element = getCommandContext(offset, psiFile) ?: return null
    var expression = element.findParentOfType<KtExpression>() ?: return null
    while (true) {
        val parent = expression.findParentOfType<KtExpression>()
        if (parent is KtExpression && parent !is KtProperty && parent.textRange.endOffset == offset) {
            expression = parent
        } else {
            if (expression.textRange.endOffset == offset) {
                break
            } else {
                return null
            }
        }
    }
    if (expression.findParentOfType<KtProperty>() == null && expression.findParentOfType<KtFunction>() == null) return null
    return expression
}

private fun findOffsetForLocalVariable(offset: Int, psiFile: PsiFile): Int? {
    var currentOffset = offset
    if (currentOffset == 0) return null
    var element = getCommandContext(offset, psiFile) ?: return null
    if (element is PsiWhiteSpace) {
        element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
    }
    currentOffset = element.textRange?.endOffset ?: currentOffset
    val localVariable = element.findParentOfType<KtProperty>() ?: return null
    if (localVariable.findParentOfType<KtFunction>() == null) return null
    if (localVariable.textRange.endOffset == currentOffset ||
        localVariable.textRange.endOffset - 1 == currentOffset ||
        localVariable.identifyingElement?.textRange?.endOffset == currentOffset
    ) {
        return currentOffset
    }
    return null
}
