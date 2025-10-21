// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.configuration

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
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.core.script.v1.*
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import java.util.function.Function
import javax.swing.JComponent


class ScriptingSupportChecker: EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.isNonScript()) return null

        val ktFile = file.toKtFile(project) ?: return null
        val featureEnabled = LanguageFeature.SkipStandaloneScriptsInSourceRoots.isEnabled(ktFile.module, project)
        val panelIsOn = featureEnabled || !decideLaterIsOn(project)

        if (panelIsOn && !compilerAllowsAnyScriptsInSourceRoots(project)
            && file.isUnderSourceRoot(project)
            && (file.isStandaloneKotlinScript(project) && file.hasNoExceptionsToBeUnderSourceRoot())
        ) {
            return Function {
                EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                    val textKey = if (featureEnabled) "kotlin.script.in.project.sources.1.9" else "kotlin.script.in.project.sources"
                    text = KotlinBundle.message(textKey)

                    createActionLabel(
                        KotlinBundle.message("kotlin.script.warning.more.info"),
                        Runnable {
                            BrowserUtil.browse(KotlinBundle.message("kotlin.script.in.project.sources.link"))
                        },
                        false
                    )
                    addMoveOutOfSourceRootAction(file, project)

                    if (!featureEnabled) {
                        addDecideLaterAction(file, project)
                    }
                }
            }
        }

        // warning panel is hidden
        if (!KotlinScriptingSettingsImpl.getInstance(project).showSupportWarning) {
            return null
        }

        if (file.hasUnknownScriptExt()) {
            return Function {
                EditorNotificationPanel(it, EditorNotificationPanel.Status.Info).apply {
                    text = KotlinBundle.message("kotlin.script.in.beta.stage")
                    createActionLabel(
                        KotlinBundle.message("kotlin.script.warning.more.info"),
                        Runnable {
                            BrowserUtil.browse(KotlinBundle.message("kotlin.script.in.beta.stage.link"))
                        },
                        false
                    )
                    addHideAction(file, project)
                }
            }
        }

        return null
    }
}

private fun EditorNotificationPanel.addHideAction(
    file: VirtualFile,
    project: Project
) {
    createActionLabel(
        KotlinBundle.message("kotlin.script.in.project.sources.hide"),
        Runnable {
            KotlinScriptingSettingsImpl.getInstance(project).showSupportWarning = false
            close(project, file)
        },
        false
    )
}

private fun EditorNotificationPanel.addDecideLaterAction(
    file: VirtualFile,
    project: Project
) {
    createActionLabel(
        KotlinBundle.message("kotlin.script.in.project.sources.later"),
        Runnable {
            KotlinScriptingSettingsImpl.getInstance(project).decideOnRemainingInSourceRootLater = true
            close(project, file)
        },
        false
    )
}

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

private fun decideLaterIsOn(project: Project): Boolean =
    KotlinScriptingSettingsImpl.getInstance(project).decideOnRemainingInSourceRootLater

private fun VirtualFile.isUnderSourceRoot(project: Project): Boolean =
    ProjectRootManager.getInstance(project).fileIndex.isUnderSourceRootOfType(this, KOTLIN_AWARE_SOURCE_ROOT_TYPES)

private fun VirtualFile.toKtFile(project: Project): KtFile? = toPsiFile(project) as? KtFile