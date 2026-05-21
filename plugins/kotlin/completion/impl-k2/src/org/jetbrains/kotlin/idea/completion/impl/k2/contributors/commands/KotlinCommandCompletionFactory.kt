// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.CommandCompletionFactory
import com.intellij.codeInsight.completion.command.commands.IntentionCommandOffsetProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ModCommandService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.projectStructure.contextModule
import org.jetbrains.kotlin.analysis.api.projectStructure.copyOrigin
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

private val ktCommandCompletionCopy: Key<Boolean> = Key.create("ktCommandCompletionCopy")

internal class KotlinCommandCompletionFactory : CommandCompletionFactory, DumbAware {
    override fun isApplicable(psiFile: PsiFile, offset: Int): Boolean {
        //many fixes don't work in kotlin, wait for fixing from the kotlin side
        if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return false
        if (offset < 1) return false
        if (psiFile !is KtFile) return false
        if (isInsideFor(psiFile, offset)) return false
        if (isInsideStringLiteral(psiFile, offset)) return false
        if (isDisabledForGradle(psiFile, offset)) return false
        return true
    }

    private fun isInsideStringLiteral(psiFile: KtFile, offset: Int): Boolean {
        val element = psiFile.findElementAt(offset)
        if (element == null) return false
        val templateExpression = PsiTreeUtil.getParentOfType(
            element,
            KtStringTemplateExpression::class.java,
            KtStringTemplateEntryWithExpression::class.java
        )
        return templateExpression is KtStringTemplateExpression
    }

    private fun isInsideFor(psiFile: KtFile, offset: Int): Boolean {
        var element = psiFile.findElementAt(offset - 1)
        val parent = element?.parent
        if (parent !is KtForExpression && parent !is KtNamedFunction) return false
        if (parent is KtForExpression) {
            element = element.prevSibling?.let {
                if (it is KtContainerNode) it.firstChild else it
            } ?: return false
            return parent.loopRange.isAncestor(element)
        }
        return false
    }

    override fun supportFiltersWithDoublePrefix(): Boolean = false

    @OptIn(KaExperimentalApi::class)
    override fun createFile(originalFile: PsiFile, text: String): PsiFile {
        val newFile =
            KtPsiFactory(originalFile.project, eventSystemEnabled = true, markGenerated = false).createFile(originalFile.name, text)
        newFile.contextModule = originalFile.getKaModule(originalFile.project, useSiteModule = null)

        val virtualFile = newFile.virtualFile
        val originalVirtualFile = originalFile.virtualFile
        if (virtualFile is LightVirtualFile && originalVirtualFile != null) {
            virtualFile.originalFile = originalVirtualFile
            virtualFile.fileType = originalVirtualFile.fileType
        }
        ktCommandCompletionCopy.set(newFile, true)
        return newFile
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

    class KotlinCommandCompletionModCommandPsiCopyHandler : ModCommandService.ModCommandPsiCopyHandler {
        @OptIn(KaExperimentalApi::class)
        override fun createCopy(file: PsiFile): PsiFile? {
            if (file !is KtFile) return null
            if (ktCommandCompletionCopy.get(file) != true) return null
            val copyOrigin = file.copyOrigin ?: return null
            val copied = copyOrigin.copied()
            val copyFileDocument = copied.fileDocument
            copyFileDocument.replaceString(0, copyFileDocument.text.length, file.fileDocument.text)
            PsiDocumentManager.getInstance(file.project).commitDocument(copyFileDocument)
            return copied

        }
    }

    private fun isDisabledForGradle(psiFile: KtFile, offset: Int): Boolean {
        if (psiFile.name != "build.gradle.kts") return false
        val element = psiFile.findElementAt(offset) ?: return false
        val isInsideDependenciesBlock = element.parentsOfType(KtLambdaArgument::class.java)
            .any {
                it.parent.asSafely<KtCallExpression>()
                    ?.referenceExpression()
                    ?.text == "dependencies"
            }
        return isInsideDependenciesBlock
    }
}
