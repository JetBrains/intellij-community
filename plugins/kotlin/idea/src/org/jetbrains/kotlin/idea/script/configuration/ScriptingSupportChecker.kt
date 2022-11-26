// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.configuration

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
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.KOTLIN_AWARE_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import java.util.function.Function
import javax.swing.JComponent
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.isStandalone

private const val ANY_SCRIPTS_IN_SOURCE_ROOTS_COMPILER_ARG = "-Xallow-any-scripts-in-source-roots"

class ScriptingSupportChecker: EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.isNonScript()) return null

        if (!decideLaterIsOn(project) && !compilerAllowsAnyScriptsInSourceRoots(project)
            && file.isUnderSourceRoot(project)
            && (file.isStandaloneKotlinScript(project) && file.hasNoExceptionsToBeUnderSourceRoot())
        ) {
            return Function {
                EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                    text = KotlinBundle.message("kotlin.script.in.project.sources")
                    createActionLabel(
                        KotlinBundle.message("kotlin.script.warning.more.info"),
                        Runnable {
                            BrowserUtil.browse(KotlinBundle.message("kotlin.script.in.project.sources.link"))
                        },
                        false
                    )
                    addMoveOutOfSourceRootAction(file, project)
                    addDecideLaterAction(file, project)
                }
            }
        }

        // warning panel is hidden
        if (!KotlinScriptingSettings.getInstance(project).showSupportWarning) {
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
            KotlinScriptingSettings.getInstance(project).showSupportWarning = false
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
            KotlinScriptingSettings.getInstance(project).decideOnRemainingInSourceRootLater = true
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

private fun VirtualFile.isStandaloneKotlinScript(project: Project): Boolean {
    val ktFile = toKtFile(project)?.takeIf(KtFile::isScript) ?: return false
    val scriptDefinition = ScriptDefinitionsManager.getInstance(project).findDefinition(KtFileScriptSource(ktFile))
        ?: return false
    return scriptDefinition.compilationConfiguration[ScriptCompilationConfiguration.isStandalone] == true
}

private fun VirtualFile.hasNoExceptionsToBeUnderSourceRoot(): Boolean =
    scriptResidenceExceptionProviders.none { it.isSupportedUnderSourceRoot(this) }

private fun VirtualFile.hasUnknownScriptExt(): Boolean =
    scriptResidenceExceptionProviders.none { it.isSupportedScriptExtension(this) }

private fun decideLaterIsOn(project: Project): Boolean =
    KotlinScriptingSettings.getInstance(project).decideOnRemainingInSourceRootLater

private fun VirtualFile.isUnderSourceRoot(project: Project): Boolean =
    ProjectRootManager.getInstance(project).fileIndex.isUnderSourceRootOfType(this, KOTLIN_AWARE_SOURCE_ROOT_TYPES)


private fun compilerAllowsAnyScriptsInSourceRoots(project: Project): Boolean {
    val additionalSettings = KotlinCompilerSettings.getInstance(project).settings
    return additionalSettings.additionalArguments.contains(ANY_SCRIPTS_IN_SOURCE_ROOTS_COMPILER_ARG)
}

private fun VirtualFile.toKtFile(project: Project): KtFile? = toPsiFile(project) as? KtFile