package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile

/**
 * Helper functions for custom PSI traversal needs.
 */
internal class MyPsiUtils {
    companion object {
        fun findAllChildren(startElement: PsiElement, condition: (PsiElement) -> Boolean, fenceCondition: (PsiElement) -> Boolean): List<PsiElement> {
            val foundChildren = mutableListOf<PsiElement>()
            startElement.accept(object: PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (startElement != element && fenceCondition(element)) return
                    if (startElement != element && condition(element)) foundChildren.add(element)
                    super.visitElement(element)
                }
            })
            return foundChildren
        }
        
        fun findAllChildren(startElement: PsiElement, condition: (PsiElement) -> Boolean): List<PsiElement>{
          return findAllChildren(startElement, condition) { false }
        }
        
        fun findParent(startElement: PsiElement, condition: (PsiElement) -> Boolean, fenceCondition: (PsiElement) -> Boolean): PsiElement? {
            var currentElement: PsiElement? = startElement
            while (currentElement != null) {
                if (fenceCondition(currentElement)) return null
                if (condition(currentElement)) return currentElement
                currentElement = currentElement.parent
            }
            return null
        }
        
        fun getFileAndLine(element: PsiElement): String {
            val document = element.containingFile.viewProvider.document
            val lineNr = document?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: -1
            return "${element.containingFile.name}:${lineNr}"
        }
        
        fun getUrl(element: PsiElement): String? {
            if (!element.isPhysical) return null
            val containingFile = if (element is PsiFileSystemItem) element else element.containingFile
            if (containingFile == null) return null
            val virtualFile = containingFile.virtualFile ?: return null
            return if (element is PsiFileSystemItem) virtualFile.url else virtualFile.url + "#" + element.textOffset
        }
        fun findRunBlockings(file: VirtualFile, project: Project): List<PsiElement> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
            return findAllChildren(psiFile) { ElementFilters.runBlockingBuilderInvocation.isAccepted(it) }
        }

        fun findNonBlockingBuilders(file: VirtualFile, project: Project): List<PsiElement> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
            return findAllChildren(psiFile) { ElementFilters.launchBuilder.isAccepted(it) || ElementFilters.asyncBuilder.isAccepted(it) }
        }

        fun findSuspendFuns(file: VirtualFile, project: Project): List<PsiElement> {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
            return findAllChildren(psiFile) { ElementFilters.suspendFun.isAccepted(it) }
        }
        
        fun getFileForElement(psiElement: PsiElement): VirtualFile {
            val ktFile = PsiTreeUtil.findFirstParent(psiElement) { it is KtFile } as KtFile
            return ktFile.virtualFile
        }
        
    }
}