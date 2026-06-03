// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KOTLIN_AWARE_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.base.util.createComponentActionLabel
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.v1.compilerAllowsAnyScriptsInSourceRoots
import org.jetbrains.kotlin.idea.core.script.v1.hasNoExceptionsToBeUnderSourceRoot
import org.jetbrains.kotlin.idea.core.script.v1.isStandaloneKotlinScript
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import java.util.function.Function
import javax.swing.JComponent

class ScriptingSupportChecker : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.isNonScript()) return null

        if (!compilerAllowsAnyScriptsInSourceRoots(project)
            && file.isUnderSourceRoot(project)
            && (file.isStandaloneKotlinScript(project) && file.hasNoExceptionsToBeUnderSourceRoot())
        ) {
            return Function {
                EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                    text = KotlinBaseScriptingBundle.message("kotlin.script.in.project.sources.1.9")

                    createActionLabel(
                        KotlinBaseScriptingBundle.message("kotlin.script.warning.more.info"),
                        Runnable {
                            BrowserUtil.browse(KotlinBundle.message("kotlin.script.in.project.sources.link"))
                        },
                        false
                    )
                    addMoveOutOfSourceRootAction(file, project)

                }
            }
        }

        return null
    }
}

private fun VirtualFile.isUnderSourceRoot(project: Project): Boolean =
    ProjectRootManager.getInstance(project).fileIndex.isUnderSourceRootOfType(this, KOTLIN_AWARE_SOURCE_ROOT_TYPES)

private fun VirtualFile.toKtFile(project: Project): KtFile? = toPsiFile(project) as? KtFile

private fun EditorNotificationPanel.addMoveOutOfSourceRootAction(
    file: VirtualFile,
    project: Project
) {
    createComponentActionLabel(KotlinBaseScriptingBundle.message("kotlin.script.in.project.sources.move")) { _ ->
        val ktFile = file.toKtFile(project) ?: return@createComponentActionLabel
        if (ktFile.isScript()) {
            showKotlinScriptMoveDialog(project, file, ktFile)
        } else {
            close(project, file)
            val dataContext = DataManager.getInstance().getDataContext(this)
            MoveHandler.doMove(project, arrayOf(ktFile), null, dataContext, null)
        }
    }
}

private fun EditorNotificationPanel.showKotlinScriptMoveDialog(
    project: Project,
    file: VirtualFile,
    ktFile: KtFile,
) {
    val targets = createKotlinScriptMoveTargets(project, file)
    val recommendedTargetIndex = targets.indexOfFirst { it.isRecommended }.takeIf { it >= 0 } ?: 0
    val selectedTarget = Messages.showDialog(
        project,
        KotlinBaseScriptingBundle.message("kotlin.script.in.project.sources.move.dialog.message", ktFile.name),
        KotlinBaseScriptingBundle.message("kotlin.script.in.project.sources.move.dialog.title"),
        null,
        targets.map { it.text }.toTypedArray(),
        recommendedTargetIndex,
        recommendedTargetIndex,
        Messages.getQuestionIcon(),
    ).let(targets::getOrNull) ?: return

    if (project.isDisposed || !ktFile.isValid) return

    when (selectedTarget) {
        is KotlinScriptMoveTarget.Directory -> {
            close(project, file)
            moveKotlinScriptFile(project, ktFile, selectedTarget.directory)
        }

        KotlinScriptMoveTarget.SelectDirectory -> chooseTargetDirectoryAndMove(project, file, ktFile)
    }
}

private fun EditorNotificationPanel.chooseTargetDirectoryAndMove(
    project: Project,
    file: VirtualFile,
    ktFile: KtFile,
) {
    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle(RefactoringBundle.message("select.target.directory"))
        .withDescription(RefactoringBundle.message("the.file.will.be.moved.to.this.directory"))
    FileChooser.chooseFile(descriptor, project, this, file.findModuleOrProjectRoot(project)) { target ->
        if (target == null || project.isDisposed || !ktFile.isValid) return@chooseFile
        val targetDirectory = PsiManager.getInstance(project).findDirectory(target) ?: return@chooseFile
        close(project, file)
        moveKotlinScriptFile(project, ktFile, targetDirectory)
    }
}

internal fun moveKotlinScriptFile(
    project: Project,
    ktFile: KtFile,
    targetDirectory: PsiDirectory,
) {
    MoveFilesOrDirectoriesProcessor(
        project,
        arrayOf(ktFile),
        targetDirectory,
        false,
        false,
        false,
        null,
        EmptyRunnable.INSTANCE,
    ).run()
}

internal fun createKotlinScriptMoveTargets(
    project: Project,
    file: VirtualFile,
): List<KotlinScriptMoveTarget> = buildList {
    val root = file.findModuleOrProjectRoot(project)
    val rootDirectory = root?.toPsiDirectory(project)
    if (rootDirectory != null) {
        add(KotlinScriptMoveTarget.Directory(KotlinBaseScriptingBundle.message("kotlin.script.in.project.sources.move.to.root"), rootDirectory, true))
    }

    val resources = file.findResourcesRoot(project)?.toPsiDirectory(project)
    if (resources != null && resources.virtualFile != root) {
        add(KotlinScriptMoveTarget.Directory(KotlinBaseScriptingBundle.message("kotlin.script.in.project.sources.move.to.resources"), resources, false))
    }

    add(KotlinScriptMoveTarget.SelectDirectory)
}

private fun VirtualFile.findModuleOrProjectRoot(project: Project): VirtualFile? {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val module = fileIndex.getModuleForFile(this)
    val contentRoots = module?.let { ModuleRootManager.getInstance(it).contentRoots.asList() }.orEmpty()
    return contentRoots.findNearestAncestor(this) ?: project.guessProjectDir()
}

private fun VirtualFile.findResourcesRoot(project: Project): VirtualFile? {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val module = fileIndex.getModuleForFile(this) ?: return null
    val rootManager = ModuleRootManager.getInstance(module)
    val contentRoot = rootManager.contentRoots.asList().findNearestAncestor(this)
    val resourceRootTypes = if (fileIndex.getContainingSourceRootType(this)?.isForTests == true) {
        listOf(JavaResourceRootType.TEST_RESOURCE, JavaResourceRootType.RESOURCE)
    } else {
        listOf(JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE)
    }

    return resourceRootTypes.firstNotNullOfOrNull { rootType ->
        rootManager.getSourceRoots(rootType).findNearestTarget(this, contentRoot)
    }
}

private fun Collection<VirtualFile>.findNearestAncestor(file: VirtualFile): VirtualFile? =
    filter { VfsUtilCore.isAncestor(it, file, false) }.maxByOrNull { it.path.length }

private fun Collection<VirtualFile>.findNearestTarget(file: VirtualFile, contentRoot: VirtualFile?): VirtualFile? =
    distinct().sortedWith(
        compareByDescending<VirtualFile> { if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, it, false)) 1 else 0 }
            .thenByDescending { commonPathPrefixLength(file, it) }
            .thenBy { it.path.length }
    ).firstOrNull()

private fun commonPathPrefixLength(first: VirtualFile, second: VirtualFile): Int =
    first.path.commonPrefixWith(second.path).length

private fun VirtualFile.toPsiDirectory(project: Project): PsiDirectory? =
    PsiManager.getInstance(project).findDirectory(this)

internal sealed interface KotlinScriptMoveTarget {
    @get:Nls
    val text: String
    val isRecommended: Boolean

    class Directory(
        @param:Nls
        override val text: String,
        val directory: PsiDirectory,
        override val isRecommended: Boolean,
    ) : KotlinScriptMoveTarget

    object SelectDirectory : KotlinScriptMoveTarget {
        override val isRecommended: Boolean = false

        override val text: String
            get() = KotlinBaseScriptingBundle.message("kotlin.script.in.project.sources.move.select.directory")
    }
}

private fun EditorNotificationPanel.close(
    project: Project,
    file: VirtualFile
) {
    val manager = FileEditorManager.getInstance(project)
    manager.getSelectedEditor(file)?.let { editor ->
        manager.removeTopComponent(editor, this)
    }
}