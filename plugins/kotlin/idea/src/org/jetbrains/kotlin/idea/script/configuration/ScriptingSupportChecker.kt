// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.configuration

import com.intellij.ide.BrowserUtil
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.util.KOTLIN_AWARE_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import java.util.function.Function
import javax.swing.JComponent

class ScriptingSupportChecker: EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
        if (!Registry.`is`("kotlin.scripting.support.warning") || file.isNonScript() || ScratchUtil.isScratch(file)) {
            return EditorNotificationProvider.CONST_NULL
        }

        // warning panel is hidden
        if (!KotlinScriptingSettings.getInstance(project).showSupportWarning) {
            return EditorNotificationProvider.CONST_NULL
        }

        val providers = ScriptingSupportCheckerProvider.CHECKER_PROVIDERS.getExtensionList(project)
        // if script file is under source root
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        if (projectFileIndex.isUnderSourceRootOfType(
                file,
                KOTLIN_AWARE_SOURCE_ROOT_TYPES
            ) && providers.none { it.isSupportedUnderSourceRoot(file) }
        ) {
            return Function {
                EditorNotificationPanel(it).apply {
                    text = KotlinBundle.message("kotlin.script.in.project.sources")
                    createActionLabel(
                        KotlinBundle.message("kotlin.script.warning.more.info"),
                        Runnable {
                            BrowserUtil.browse(KotlinBundle.message("kotlin.script.in.project.sources.link"))
                        },
                        false
                    )
                    addHideAction(file, project)
                }
            }
        }

        if (providers.none { it.isSupportedScriptExtension(file) }) {
            return Function {
                EditorNotificationPanel(it).apply {
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

        return EditorNotificationProvider.CONST_NULL
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
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.getSelectedEditor(file)?.let { editor ->
                fileEditorManager.removeTopComponent(editor, this)
            }
        },
        false
    )
}

private fun VirtualFile.supportedScriptExtensions() =
    name.endsWith(".main.kts") || name.endsWith(".space.kts") || name.endsWith(".gradle.kts")