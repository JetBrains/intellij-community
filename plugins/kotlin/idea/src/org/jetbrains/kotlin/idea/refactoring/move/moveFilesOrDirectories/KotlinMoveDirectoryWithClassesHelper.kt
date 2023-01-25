// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.moveFilesOrDirectories

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesHelper
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Function
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.refactoring.invokeOnceOnCommandFinish
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.KotlinDirectoryMoveTarget
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.MoveKotlinDeclarationsProcessor
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.analyzeConflictsInFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

class KotlinMoveDirectoryWithClassesHelper : MoveDirectoryWithClassesHelper() {
    private data class FileUsagesWrapper(
        val psiFile: KtFile,
        val usages: List<UsageInfo>,
        val moveDeclarationsProcessor: MoveKotlinDeclarationsProcessor?
    ) : UsageInfo(psiFile)

    private class MoveContext(
        val newParent: PsiDirectory,
        val moveDeclarationsProcessor: MoveKotlinDeclarationsProcessor?
    )

    private val fileHandler = MoveKotlinFileHandler()

    private var fileToMoveContext: MutableMap<PsiFile, MoveContext>? = null

    private fun getOrCreateMoveContextMap(): MutableMap<PsiFile, MoveContext> {
        return fileToMoveContext ?: HashMap<PsiFile, MoveContext>().apply {
            fileToMoveContext = this
            invokeOnceOnCommandFinish { fileToMoveContext = null }
        }
    }

    override fun findUsages(
      filesToMove: MutableCollection<out PsiFile>,
      directoriesToMove: Array<out PsiDirectory>,
      result: MutableCollection<in UsageInfo>,
      searchInComments: Boolean,
      searchInNonJavaFiles: Boolean,
      project: Project
    ) {
        filesToMove
            .filterIsInstance<KtFile>()
            .mapTo(result) { file ->
                val usages = fileHandler.findUsages(
                    file,
                    newParent = null,
                    withConflicts = false,
                    searchInCommentsAndStrings = searchInComments,
                    searchInNonJavaFiles = searchInNonJavaFiles,
                )
                FileUsagesWrapper(file, usages, null)
            }
    }

    override fun preprocessUsages(
        project: Project,
        files: MutableSet<PsiFile>,
        infos: Array<UsageInfo>,
        directory: PsiDirectory?,
        conflicts: MultiMap<PsiElement, String>
    ) {
        val psiPackage = directory?.getPackage() ?: return
        val moveTarget = KotlinDirectoryMoveTarget(FqName(psiPackage.qualifiedName), directory.virtualFile)
        for ((index, usageInfo) in infos.withIndex()) {
            if (usageInfo !is FileUsagesWrapper) continue

            ProgressManager.getInstance().progressIndicator?.text2 = KotlinBundle.message("text.processing.file.0", usageInfo.psiFile.name)

            runReadAction {
                analyzeConflictsInFile(usageInfo.psiFile, usageInfo.usages, moveTarget, files, conflicts) {
                    infos[index] = usageInfo.copy(usages = it)
                }
            }
        }
    }

    override fun beforeMove(psiFile: PsiFile) {

    }

    // Actual move logic is implemented in postProcessUsages since usages are not available here
    override fun move(
      file: PsiFile,
      moveDestination: PsiDirectory,
      oldToNewElementsMapping: MutableMap<PsiElement, PsiElement>,
      movedFiles: MutableList<in PsiFile>,
      listener: RefactoringElementListener?
    ): Boolean {
        if (file !is KtFile) return false

        val moveDeclarationsProcessor = fileHandler.initMoveProcessor(file, moveDestination, false)
        val moveContextMap = getOrCreateMoveContextMap()
        moveContextMap[file] = MoveContext(moveDestination, moveDeclarationsProcessor)
        if (moveDeclarationsProcessor != null) {
            moveDestination.getFqNameWithImplicitPrefix()?.quoteIfNeeded()?.let {
                file.packageDirective?.fqName = it
            }
        }
        return true
    }

    override fun afterMove(newElement: PsiElement) {

    }

    override fun postProcessUsages(usages: Array<out UsageInfo>, newDirMapper: Function<in PsiDirectory, out PsiDirectory>) {
        val fileToMoveContext = fileToMoveContext ?: return
        try {
            val usagesToProcess = ArrayList<FileUsagesWrapper>()
            usages
                .filterIsInstance<FileUsagesWrapper>()
                .forEach body@{
                    val file = it.psiFile
                    val moveContext = fileToMoveContext[file] ?: return@body

                    MoveFilesOrDirectoriesUtil.doMoveFile(file, moveContext.newParent)

                    val moveDeclarationsProcessor = moveContext.moveDeclarationsProcessor ?: return@body
                    val movedFile = moveContext.newParent.findFile(file.name) ?: return@body

                    usagesToProcess += FileUsagesWrapper(movedFile as KtFile, it.usages, moveDeclarationsProcessor)
                }
            usagesToProcess.forEach { fileHandler.retargetUsages(it.usages, it.moveDeclarationsProcessor!!) }
        } finally {
            this.fileToMoveContext = null
        }
    }
}