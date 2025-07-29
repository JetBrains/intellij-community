// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.CommandCompletionFactory
import com.intellij.codeInsight.completion.command.commands.IntentionCommandOffsetProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.analysis.api.projectStructure.contextModule
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

class KotlinCommandCompletionFactory : CommandCompletionFactory, DumbAware {
    override fun isApplicable(psiFile: PsiFile, offset: Int): Boolean {
        if (offset < 1) return false
        if (psiFile !is KtFile) return false
        var element = psiFile.findElementAt(offset - 1)
        val parent = element?.parent
        if (parent !is KtForExpression && parent !is KtNamedFunction) return true
        if (parent is KtForExpression) {
            element = element.prevSibling?.let {
                if (it is KtContainerNode) it.firstChild else it
            } ?: return true
            return !parent.loopRange.isAncestor(element)
        }
        return true
    }

    override fun supportFiltersWithDoublePrefix(): Boolean = false

    @OptIn(KaImplementationDetail::class, KaExperimentalApi::class)
    override fun createFile(originalFile: PsiFile, text: String): PsiFile {
        val newFile =
            KtPsiFactory(originalFile.project, eventSystemEnabled = true, markGenerated = false).createFile(originalFile.name, text)
        newFile.contextModule = originalFile.getKaModule(originalFile.project, useSiteModule = null)
        if (originalFile.name.endsWith(".kts")) {
            createCopyOfScript(originalFile, newFile)?.let { return it }
        }

        val virtualFile = newFile.virtualFile
        val originalVirtualFile = originalFile.virtualFile
        if (virtualFile is LightVirtualFile && originalVirtualFile != null) {
            virtualFile.analysisContextModule = originalFile.getKaModule(originalFile.project, useSiteModule = null)
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

    class KotlinIntentionCommandOffsetProvider : IntentionCommandOffsetProvider {
        override fun findOffsets(psiFile: PsiFile, offset: Int): List<Int> {
            val offsets = mutableListOf<Int>()
            offsets.add(offset)
            if (offset == 0) {
                return offsets
            }
            val previousElement = psiFile.findElementAt(offset - 1)
            if (previousElement?.parent is PsiLanguageInjectionHost) {
                val delta = if (previousElement.parent.text.endsWith("\"\"\"")) {
                    3
                } else {
                    1
                }
                offsets.add(previousElement.parent.range.endOffset - delta)
            }
            return offsets
        }
    }
}
