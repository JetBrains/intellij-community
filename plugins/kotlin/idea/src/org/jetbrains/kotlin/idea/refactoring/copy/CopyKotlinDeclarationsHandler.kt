// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.copy

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.*
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesDialog
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.core.createKotlinFile
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.packageMatchesDirectoryOrImplicit
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

class CopyKotlinDeclarationsHandler : AbstractCopyKotlinDeclarationsHandler() {
    override fun createFile(
        targetFileName: String,
        targetDirectory: PsiDirectory
    ): KtFile {
        return createKotlinFile(targetFileName, targetDirectory)
    }

    private data class TargetData(
        val openInEditor: Boolean,
        val newName: String,
        val targetDirWrapper: AutoCreatingPsiDirectoryWrapper,
        val targetSourceRoot: VirtualFile?
    )

    private data class SourceData(
        val project: Project,
        val singleElementToCopy: KtElement?,
        val elementsToCopy: List<KtElement>,
        val originalFile: KtFile,
        val initialTargetDirectory: PsiDirectory
    )

    private fun getTargetDataForUnitTest(sourceData: SourceData): TargetData? {
        with(sourceData) {
            val targetSourceRoot: VirtualFile = initialTargetDirectory.sourceRoot ?: return null
            val newName: String = project.copyNewName ?: singleElementToCopy?.name ?: originalFile.name
            if (singleElementToCopy != null && newName.isEmpty()) return null
            return TargetData(
                openInEditor = false,
                newName = newName,
                targetDirWrapper = initialTargetDirectory.toDirectoryWrapper(),
                targetSourceRoot = targetSourceRoot
            )
        }
    }

    private fun getTargetDataForUX(sourceData: SourceData): TargetData? {

        val openInEditor: Boolean
        val newName: String?
        val targetDirWrapper: AutoCreatingPsiDirectoryWrapper?
        val targetSourceRoot: VirtualFile?

        val singleNamedSourceElement = sourceData.singleElementToCopy as? KtNamedDeclaration

        if (singleNamedSourceElement !== null) {
            val dialog = CopyKotlinDeclarationDialog(singleNamedSourceElement, sourceData.initialTargetDirectory, sourceData.project)
            dialog.title = copyCommandName
            if (!dialog.showAndGet()) return null

            openInEditor = dialog.openInEditor
            newName = dialog.newName
            targetDirWrapper = dialog.targetDirectory?.toDirectoryWrapper()
            targetSourceRoot = dialog.targetSourceRoot
        } else {
            val dialog = CopyFilesOrDirectoriesDialog(
                arrayOf(sourceData.originalFile),
                sourceData.initialTargetDirectory,
                sourceData.project,
                /*doClone = */false
            )
            if (!dialog.showAndGet()) return null
            openInEditor = dialog.openInEditor()
            newName = dialog.newName
            targetDirWrapper = dialog.targetDirectory?.toDirectoryWrapper()
            targetSourceRoot = dialog.targetDirectory?.sourceRoot
        }

        targetDirWrapper ?: return null
        newName ?: return null

        if (sourceData.singleElementToCopy != null && newName.isEmpty()) return null

        return TargetData(
            openInEditor = openInEditor,
            newName = newName,
            targetDirWrapper = targetDirWrapper,
            targetSourceRoot = targetSourceRoot
        )
    }

    private fun collectInternalUsages(sourceData: SourceData, targetData: TargetData) = runReadAction {
        val targetPackageName = targetData.targetDirWrapper.getPackageName()
        val changeInfo = MoveContainerChangeInfo(
            MoveContainerInfo.Package(sourceData.originalFile.packageFqName),
            MoveContainerInfo.Package(FqName(targetPackageName))
        )
        sourceData.elementsToCopy.flatMapTo(LinkedHashSet()) { elementToCopy ->
            elementToCopy.getInternalReferencesToUpdateOnPackageNameChange(changeInfo).filter {
                val referencedElement = (it as? MoveRenameUsageInfo)?.referencedElement
                referencedElement == null || !elementToCopy.isAncestor(referencedElement)
            }
        }
    }

    private fun trackedCopyFiles(sourceFiles: Array<out PsiFileSystemItem>, initialTargetDirectory: PsiDirectory?): Set<VirtualFile> {

        if (!copyFilesHandler.canCopy(sourceFiles)) return emptySet()

        val mapper = object : VirtualFileListener {
            val filesCopied = mutableSetOf<VirtualFile>()

            override fun fileCopied(event: VirtualFileCopyEvent) {
                filesCopied.add(event.file)
            }

            override fun fileCreated(event: VirtualFileEvent) {
                filesCopied.add(event.file)
            }
        }

        with(VirtualFileManager.getInstance()) {
            try {
                addVirtualFileListener(mapper)
                copyFilesHandler.doCopy(sourceFiles, initialTargetDirectory)
            } finally {
                removeVirtualFileListener(mapper)
            }
        }
        return mapper.filesCopied
    }

    private fun doCopyFiles(filesToCopy: Array<out PsiFileSystemItem>, initialTargetDirectory: PsiDirectory?) {

        if (filesToCopy.isEmpty()) return

        val project = filesToCopy[0].project
        val psiManager = PsiManager.getInstance(project)

        project.executeCommand(copyCommandName) {
            val copiedFiles = trackedCopyFiles(filesToCopy, initialTargetDirectory)

            copiedFiles.forEach { copiedFile ->
                val targetKtFile = psiManager.findFile(copiedFile) as? KtFile
                if (targetKtFile !== null) {
                    runWriteAction {
                        if (!targetKtFile.packageMatchesDirectoryOrImplicit()) {
                            targetKtFile.containingDirectory?.getFqNameWithImplicitPrefix()?.quoteIfNeeded()?.let { targetDirectoryFqName ->
                                targetKtFile.packageFqName = targetDirectoryFqName
                            }
                        }
                        performDelayedRefactoringRequests(project)
                    }
                }
            }
        }
    }

    override fun doCopy(elements: Array<out PsiElement>, defaultTargetDirectory: PsiDirectory?) {

        if (elements.isEmpty()) return

        if (!canCopyDeclarations(elements)) {
            getSourceFiles(elements)?.let {
                return doCopyFiles(it, defaultTargetDirectory)
            }
        }

        val elementsToCopy = elements.mapNotNull { it.getCopyableElement() }
        if (elementsToCopy.isEmpty()) return

        val singleElementToCopy = elementsToCopy.singleOrNull()

        val originalFile = elementsToCopy.first().containingFile as KtFile
        val initialTargetDirectory = defaultTargetDirectory ?: originalFile.containingDirectory ?: return

        val project = initialTargetDirectory.project

        val sourceData = SourceData(
            project = project,
            singleElementToCopy = singleElementToCopy,
            elementsToCopy = elementsToCopy,
            originalFile = originalFile,
            initialTargetDirectory = initialTargetDirectory
        )

        val targetData = if (isUnitTestMode()) getTargetDataForUnitTest(sourceData) else getTargetDataForUX(sourceData)
        targetData ?: return

        val internalUsages = collectInternalUsages(sourceData, targetData)
        markInternalUsages(internalUsages)

        val conflicts = collectConflicts(sourceData, targetData, internalUsages)

        project.checkConflictsInteractively(conflicts) {
            try {
                project.executeCommand(copyCommandName) {
                    doRefactor(sourceData, targetData)
                }
            } finally {
                cleanUpInternalUsages(internalUsages)
            }
        }
    }

    private data class RefactoringResult(
        val targetFile: PsiFile,
        val copiedDeclaration: KtNamedDeclaration?,
        val restoredInternalUsages: List<UsageInfo>? = null
    )

    private fun getTargetFileName(sourceData: SourceData, targetData: TargetData) =
        if (targetData.newName.contains(".")) targetData.newName
        else targetData.newName + "." + sourceData.originalFile.virtualFile.extension

    private fun doRefactor(sourceData: SourceData, targetData: TargetData) {

        var refactoringResult: RefactoringResult? = null
        try {
            val targetDirectory = runWriteAction {
                targetData.targetDirWrapper.getOrCreateDirectory(sourceData.initialTargetDirectory)
            }

            val targetFileName = getTargetFileName(sourceData, targetData)

            val isSingleDeclarationInFile =
                sourceData.singleElementToCopy is KtNamedDeclaration &&
                        sourceData.originalFile.declarations.singleOrNull() == sourceData.singleElementToCopy

            val fileToCopy = when {
                sourceData.singleElementToCopy is KtFile -> sourceData.singleElementToCopy
                isSingleDeclarationInFile -> sourceData.originalFile
                else -> null
            }

            refactoringResult = if (fileToCopy !== null) {
                doRefactoringOnFile(fileToCopy, sourceData, targetDirectory, targetFileName, isSingleDeclarationInFile)
            } else {
                val targetFile = getOrCreateTargetFile(sourceData.originalFile, targetDirectory, targetFileName)
                    ?: throw IncorrectOperationException("Could not create target file.")
                doRefactoringOnElement(sourceData, targetFile)
            }

            refactoringResult.copiedDeclaration?.let<KtNamedDeclaration, Unit> { newDeclaration ->
                if (targetData.newName == newDeclaration.name) return@let
                val selfReferences = ReferencesSearch.search(newDeclaration, LocalSearchScope(newDeclaration)).findAll()
                runWriteAction {
                    selfReferences.forEach { it.handleElementRename(targetData.newName) }
                    newDeclaration.setName(targetData.newName)
                }
            }

            if (targetData.openInEditor) {
                EditorHelper.openInEditor(refactoringResult.targetFile)
            }
        } catch (e: IncorrectOperationException) {
            Messages.showMessageDialog(sourceData.project, e.message, RefactoringBundle.message("error.title"), Messages.getErrorIcon())
        } finally {
            refactoringResult?.restoredInternalUsages?.let { cleanUpInternalUsages(it) }
        }
    }


    private fun doRefactoringOnFile(
        fileToCopy: KtFile,
        sourceData: SourceData,
        targetDirectory: PsiDirectory,
        targetFileName: String,
        isSingleDeclarationInFile: Boolean
    ): RefactoringResult {
        val targetFile = runWriteAction {
            // implicit package prefix may change after copy
            val targetDirectoryFqName = targetDirectory.getFqNameWithImplicitPrefix()
            val copiedFile = targetDirectory.copyFileFrom(targetFileName, fileToCopy)
            if (copiedFile is KtFile && fileToCopy.packageMatchesDirectoryOrImplicit()) {
                targetDirectoryFqName?.quoteIfNeeded()?.let { copiedFile.packageFqName = it }
            }

            performDelayedRefactoringRequests(sourceData.project)
            copiedFile
        }

        val copiedDeclaration = if (isSingleDeclarationInFile && targetFile is KtFile) {
            targetFile.declarations.singleOrNull() as? KtNamedDeclaration
        } else null

        return RefactoringResult(targetFile, copiedDeclaration)
    }

    private fun doRefactoringOnElement(
        sourceData: SourceData,
        targetFile: KtFile
    ): RefactoringResult {
        val restoredInternalUsages = ArrayList<UsageInfo>()
        val oldToNewElementsMapping = HashMap<PsiElement, PsiElement>()

        runWriteAction {
            val newElements = sourceData.elementsToCopy.map { targetFile.add(it.copy()) as KtNamedDeclaration }
            sourceData.elementsToCopy.zip(newElements).toMap(oldToNewElementsMapping)
            oldToNewElementsMapping[sourceData.originalFile] = targetFile

            for (newElement in oldToNewElementsMapping.values) {
                restoredInternalUsages += restoreInternalUsages(newElement as KtElement, oldToNewElementsMapping, forcedRestore = true)
                postProcessMoveUsages(restoredInternalUsages, oldToNewElementsMapping)
            }

            performDelayedRefactoringRequests(sourceData.project)
        }

        val copiedDeclaration = oldToNewElementsMapping.values.filterIsInstance<KtNamedDeclaration>().singleOrNull()

        return RefactoringResult(targetFile, copiedDeclaration, restoredInternalUsages)
    }

    private fun collectConflicts(
        sourceData: SourceData,
        targetData: TargetData,
        internalUsages: HashSet<UsageInfo>
    ): MultiMap<PsiElement, String> {

        if (isUnitTestMode() && BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore())
            return MultiMap.empty()

        val targetSourceRootPsi = targetData.targetSourceRoot?.toPsiDirectory(sourceData.project)
            ?: return MultiMap.empty()

        if (sourceData.project != sourceData.originalFile.project) return MultiMap.empty()

        val moveCheckerInfo = KotlinMoveConflictCheckerInfo(
            sourceData.project,
            sourceData.elementsToCopy,
            KotlinMoveTarget.Directory(FqName.ROOT, targetSourceRootPsi.virtualFile),
            sourceData.originalFile
        )

        return MultiMap<PsiElement, String>().apply {
            putAllValues(checkModuleConflictsInDeclarations(moveCheckerInfo, internalUsages))
            putAllValues(checkVisibilityInDeclarations(moveCheckerInfo))
        }
    }

    override fun doClone(element: PsiElement) {

    }
}
