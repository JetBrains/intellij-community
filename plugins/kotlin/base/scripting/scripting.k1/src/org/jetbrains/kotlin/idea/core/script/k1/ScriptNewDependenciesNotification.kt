// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper

fun VirtualFile.removeScriptDependenciesNotificationPanel(project: Project) {
    withSelectedEditor(project) { manager ->
        notificationPanel?.let {
            manager.removeTopComponent(this, it)
        }
        notificationPanel = null
    }
}

fun VirtualFile.addScriptDependenciesNotificationPanel(
    compilationConfigurationResult: ScriptCompilationConfigurationWrapper,
    project: Project,
    onClick: () -> Unit
) {
    withSelectedEditor(project) { manager ->
        val existingPanel = notificationPanel
        if (existingPanel != null) {
            if (existingPanel.compilationConfigurationResult == compilationConfigurationResult) {
                return@withSelectedEditor
            }
            notificationPanel?.let {
                manager.removeTopComponent(this, it)
            }
        }

        val panel = NewScriptDependenciesNotificationPanel(onClick, compilationConfigurationResult, project, this@addScriptDependenciesNotificationPanel)
        notificationPanel = panel
        manager.addTopComponent(this, panel)
    }
}

@TestOnly
fun VirtualFile.hasSuggestedScriptConfiguration(project: Project): Boolean {
    return FileEditorManager.getInstance(project).getSelectedEditor(this)?.notificationPanel != null
}

@TestOnly
fun VirtualFile.applySuggestedScriptConfiguration(project: Project): Boolean {
    val notificationPanel = FileEditorManager.getInstance(project).getSelectedEditor(this)?.notificationPanel
        ?: return false
    notificationPanel.onClick.invoke()

    return true
}

private fun VirtualFile.withSelectedEditor(project: Project, f: FileEditor.(FileEditorManager) -> Unit) {
    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater

        val fileEditorManager = FileEditorManager.getInstance(project)
        (fileEditorManager.getSelectedEditor(this))?.let {
            f(it, fileEditorManager)
        }
    }
}

private var FileEditor.notificationPanel: NewScriptDependenciesNotificationPanel? by UserDataProperty<FileEditor, NewScriptDependenciesNotificationPanel>(Key.create("script.dependencies.panel"))

private class NewScriptDependenciesNotificationPanel(
    val onClick: () -> Unit,
    val compilationConfigurationResult: ScriptCompilationConfigurationWrapper,
    project: Project,
    file: VirtualFile
) : EditorNotificationPanel(Status.Info) {

    init {
        text = KotlinBaseScriptingBundle.message("notification.text.there.is.a.new.script.context.available")
        createActionLabel(KotlinBaseScriptingBundle.message("notification.action.text.apply.context")) {
            onClick()
        }

        createActionLabel(KotlinBaseScriptingBundle.message("notification.action.text.enable.auto.reload")) {
            onClick()

            @Suppress("DEPRECATION")
            val scriptDefinition = file.findScriptDefinition(project) ?: return@createActionLabel
            KotlinScriptingSettingsImpl.getInstance(project).setAutoReloadConfigurations(scriptDefinition, true)
        }
    }
}
