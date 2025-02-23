// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.CommandCompletionFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

class KotlinCommandCompletionFactory : CommandCompletionFactory, DumbAware {
    override fun isApplicable(psiFile: PsiFile, offset: Int): Boolean {
        if (psiFile !is KtFile) return false
        var element = psiFile.findElementAt(offset)
        val parent = element?.parent
        if (parent !is KtForExpression) return true
        element = element.prevSibling?.let {
            if (it is KtContainerNode) it.firstChild else it
        } ?: return true
        return !parent.loopRange.isAncestor(element)
    }

    override fun createFile(originalFile: PsiFile, text: String): PsiFile? {
        val newFile = KtPsiFactory.contextual(originalFile, eventSystemEnabled = true).createFile(originalFile.name, text)
        newFile.originalFile = originalFile
        if (originalFile.name.endsWith(".kts")) {
            createCopyOfScript(originalFile, newFile)?.let { return it }
        }

        val virtualFile = newFile.virtualFile
        val originalVirtualFile = originalFile.virtualFile
        if (virtualFile is LightVirtualFile && originalVirtualFile != null) {
            virtualFile.originalFile = originalVirtualFile
            virtualFile.fileType = originalVirtualFile.fileType

        }
        return newFile
    }

    private fun createCopyOfScript(originalFile: PsiFile, newFile: KtFile): KtFile? {
        // We copy the original file, which retains the correct script context
        val newFileCopy = originalFile.copied() as KtFile
        // replace the old script block with the new script block
        val copyOfOriginalBlockExpression = newFileCopy.script?.blockExpression ?: return null
        val copiedNewBlockExpression = newFile.script?.blockExpression ?: return null
        copyOfOriginalBlockExpression.replace(copiedNewBlockExpression.copy())
        return newFileCopy
    }
}
