// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.move.MoveHandler
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KOTLIN_AWARE_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.script.v1.compilerAllowsAnyScriptsInSourceRoots
import org.jetbrains.kotlin.idea.core.script.v1.hasNoExceptionsToBeUnderSourceRoot
import org.jetbrains.kotlin.idea.core.script.v1.isEnabled
import org.jetbrains.kotlin.idea.core.script.v1.isStandaloneKotlinScript
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import java.util.function.Function
import javax.swing.JComponent

class ScriptingSupportChecker : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.isNonScript()) return null

        val ktFile = file.toKtFile(project) ?: return null
        val featureEnabled = LanguageFeature.SkipStandaloneScriptsInSourceRoots.isEnabled(ktFile.module, project)

        if (featureEnabled && !compilerAllowsAnyScriptsInSourceRoots(project)
            && file.isUnderSourceRoot(project)
            && (file.isStandaloneKotlinScript(project) && file.hasNoExceptionsToBeUnderSourceRoot())
        ) {
            return Function {
                EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                    text = KotlinBundle.message("kotlin.script.in.project.sources.1.9")

                    createActionLabel(
                        KotlinBundle.message("kotlin.script.warning.more.info"),
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
    createActionLabel(
        KotlinBundle.message("kotlin.script.in.project.sources.move"),
        Runnable {
            close(project, file)
            val dataContext = DataManager.getInstance().getDataContext(this)
            MoveHandler.doMove(project, arrayOf(file.toKtFile(project)), null, dataContext, null)
        },
        false
    )
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