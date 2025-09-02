// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractCopyClassCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractMoveCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinMoveCompletionCommandProvider : AbstractMoveCompletionCommandProvider() {
    override fun findMoveClassOffset(offset: Int, psiFile: PsiFile): Int? {
        var currentOffset = offset
        if (currentOffset == 0) return null
        var element = getCommandContext(offset, psiFile) ?: return null
        if (element is PsiWhiteSpace) {
            element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
        }
        currentOffset = element.textRange?.endOffset ?: currentOffset
        val clazz = element.findParentOfType<KtClass>()
        val lBrace = clazz?.body?.lBrace
        if (lBrace != null && (clazz.textRange.endOffset == currentOffset ||
                    (lBrace.textRange?.startOffset ?: 0) > currentOffset)
        ) {
            return clazz.nameIdentifier?.textRange?.endOffset
        }
        val method = element.parentOfType<KtNamedFunction>()
        if (method == null) return null
        if ((method.valueParameterList?.textRange?.endOffset ?: 0) >= currentOffset ||
            method.textRange?.endOffset == currentOffset
        ) return method.valueParameterList?.textRange?.endOffset
        return null
    }
}

class KotlinCopyCompletionCommandProvider : AbstractCopyClassCompletionCommandProvider() {
    override fun findMoveClassOffset(offset: Int, psiFile: PsiFile): Int? {
        var currentOffset = offset
        if (currentOffset == 0) return null
        var element = getCommandContext(offset, psiFile) ?: return null
        if (element is PsiWhiteSpace) {
            element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
        }
        currentOffset = element.textRange?.endOffset ?: currentOffset
        val clazz = element.findParentOfType<KtClass>()
        val lBrace = clazz?.body?.lBrace
        if (lBrace != null && (clazz.textRange.endOffset == currentOffset ||
                    (lBrace.textRange?.startOffset ?: 0) > currentOffset)
        ) {
            return clazz.nameIdentifier?.textRange?.endOffset
        }
        val method = element.parentOfType<KtNamedFunction>()
        if (method == null || method.parent !is KtFile) return null
        if ((method.valueParameterList?.textRange?.endOffset ?: 0) >= currentOffset ||
            method.textRange?.endOffset == currentOffset
        ) return method.valueParameterList?.textRange?.endOffset
        return null
    }
}