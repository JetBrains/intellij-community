// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractExtractMethodCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractParameterCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class KotlinExtractParameterCompletionCommandProvider : AbstractExtractParameterCompletionCommandProvider() {
    override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
        return findOffsetForLocalVariable(offset, psiFile)
    }
}

internal class KotlinExtractMethodCompletionCommandProvider : AbstractExtractMethodCompletionCommandProvider(
    actionId = "ExtractFunction",
    presentableName = KotlinBundle.message("action.ExtractFunction.text"),
    previewText = KotlinBundle.message("action.ExtractFunction.command.completion.description"),
) {

    override fun findControlFlowStatement(offset: Int, psiFile: PsiFile): PsiElement? {
        val element = getCommandContext(offset, psiFile) ?: return null
        val elementType = element.elementType
        if (elementType != KtTokens.LBRACE && elementType != KtTokens.RBRACE) return null

        val parent = element.parent
        if (parent !is KtBlockExpression) return null

        val containerNode = parent.parent
        if (containerNode !is KtContainerNodeForControlStructureBody) return null

        val controlFlowExpression = containerNode.parent
        if (controlFlowExpression is KtLoopExpression) return controlFlowExpression
        else if (controlFlowExpression is KtIfExpression) {
            var expression = controlFlowExpression
            while (true) {
                val parent = expression.parent
                val grandParent = parent?.parent
                if (parent is KtContainerNodeForControlStructureBody && grandParent is KtIfExpression) expression = grandParent else return expression
            }
        }
        return null
    }

    override fun findOutermostExpression(
        offset: Int,
        psiFile: PsiFile,
        editor: Editor?
    ): PsiElement? {
        val expression = findExpressionInsideMethod(offset, psiFile)
        if (expression?.findParentOfType<KtFunction>() != null || expression?.findParentOfType<KtProperty>() != null) return expression
        return null
    }
}

private fun findExpressionInsideMethod(offset: Int, psiFile: PsiFile): KtExpression? {
    val element = getCommandContext(offset, psiFile) ?: return null
    var expression = element.findParentOfType<KtExpression>() ?: return null

    while (true) {
        val parent = PsiTreeUtil.getParentOfType(expression, KtExpression::class.java, true, KtValueArgumentList::class.java)
        if (parent is KtExpression && parent !is KtProperty && parent.textRange.endOffset == offset || parent is KtCallExpression) {
            expression = parent
        } else {
            if (expression.textRange.endOffset == offset || expression is KtCallExpression) {
                return expression
            } else {
                return null
            }
        }
    }
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
