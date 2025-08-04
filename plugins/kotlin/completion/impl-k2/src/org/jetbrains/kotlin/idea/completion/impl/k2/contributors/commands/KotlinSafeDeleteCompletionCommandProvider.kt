// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractSafeDeleteCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.DirectInspectionFixCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction

internal class KotlinSafeDeleteCompletionCommandProvider : AbstractSafeDeleteCompletionCommandProvider() {
    override fun findElement(
        offset: Int,
        psiFile: PsiFile,
        editor: Editor?
    ): PsiElement? {
        var currentOffset = offset
        if (currentOffset == 0 || editor == null) return null
        var element = getCommandContext(offset, psiFile) ?: return null
        if (element is PsiWhiteSpace) {
            element = PsiTreeUtil.prevVisibleLeaf(element) ?: return null
            currentOffset = element.textRange.endOffset
        }

        val method = element.parentOfType<KtFunction>()
        if (method != null &&
            (method.identifyingElement?.textRange?.endOffset == currentOffset ||
                    method.bodyBlockExpression?.textRange?.endOffset == currentOffset)
        ) return method.identifyingElement

        val psiClass = element.parentOfType<KtClass>()
        if (psiClass != null && psiClass.getBody()?.rBrace != null && psiClass.getBody()?.rBrace?.textRange?.endOffset == currentOffset) {
            return psiClass.identifyingElement
        }

        val nameIdentifierOwner = element.parentOfType<PsiNameIdentifierOwner>()
        if (nameIdentifierOwner != null && nameIdentifierOwner.nameIdentifier?.textRange?.endOffset == currentOffset) {
            return nameIdentifierOwner.nameIdentifier
        }

        val targetContext = getTargetContext(offset, editor)
        if (targetContext?.isWritable != true) return null
        return element
    }

    override fun skipForHighlightCommand(command: CompletionCommand): Boolean {
        return command is DirectInspectionFixCompletionCommand &&
                (command.inspectionId == "UnusedVariable" || command.inspectionId == "unused")
    }
}