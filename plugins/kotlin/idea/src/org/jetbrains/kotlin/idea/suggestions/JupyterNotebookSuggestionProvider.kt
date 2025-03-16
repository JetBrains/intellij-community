// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.suggestions

import com.google.gson.JsonParser
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications


private val JupyterAndNotebookFilesEnablementChecker: PluginEnablementChecker = PluginEnablementChecker(
    notebookLanguages = null,
    dismissedKey = "jupyter.suggestion.dismissed",
    suggestionText = IdeBundle.message("plugins.advertiser.plugins.found", "*.ipynb"),
    suggestionActionText = IdeBundle.message("plugins.advertiser.action.enable.plugins"),
    requiresIdeRestart = true,
    pluginIds = listOf(
        "intellij.jupyter",
        "com.intellij.notebooks.core",
    )
)

private val KotlinNotebookEnablementChecker: PluginEnablementChecker = PluginEnablementChecker(
    notebookLanguages = setOf("kotlin"),
    dismissedKey = "notebook.kotlin.suggestion.dismissed",
    suggestionText = IdeBundle.message("plugins.advertiser.plugins.found", "Kotlin Notebook (*.ipynb)"),
    suggestionActionText = IdeBundle.message("plugins.advertiser.action.install.plugin.name", "Kotlin Notebook"),
    requiresIdeRestart = true,
    pluginIds = listOf(
        "org.jetbrains.plugins.kotlin.jupyter",
        "org.jetbrains.kotlin",
    ) + JupyterAndNotebookFilesEnablementChecker.pluginIds,
)

private val enablementCheckers = listOf(
    KotlinNotebookEnablementChecker,
    JupyterAndNotebookFilesEnablementChecker,
)

/**
 * Suggestion provider for the Notebooks-related plugins (Kotlin Notebook or Jupyter + Notebook Files).
 */
internal class JupyterNotebookSuggestionProvider : PluginSuggestionProvider {

    override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
        // Copy the file extension from `com.intellij.jupyter.core.jupyter.JupyterFileType` to avoid
        // having a dependency on the Jupyter module.
        if (file.extension != "ipynb") return null
        if (enablementCheckers.any { it.pluginsInstalledAndEnabled() }) return null
        if (enablementCheckers.any { it.isSuggestionDismissed() }) return null

        // We should only consider notebooks configured to use the Kotlin kernel.
        // Any error in the JSON file or format will be ignored as it is handled as part
        // of opening the file in the editor.
        val notebookLanguage = try {
            val json = JsonParser.parseString(file.readText())
            json.asJsonObject["metadata"]
                ?.asJsonObject?.get("language_info")
                ?.asJsonObject?.get("name")
                ?.asString
                ?.lowercase()
        } catch (_: Throwable) {
            return null
        }

        val enablementChecker = enablementCheckers.first { checker ->
            checker.notebookLanguages == null ||
                    notebookLanguage != null && notebookLanguage in checker.notebookLanguages
        }
        return JupyterPluginSuggestion(project, enablementChecker)
    }
}

private class JupyterPluginSuggestion(
    private val project: Project,
    private val checker: PluginEnablementChecker,
): PluginSuggestion {
    override val pluginIds: List<String> = checker.pluginIds

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
        val status = EditorNotificationPanel.Status.Info
        val panel = EditorNotificationPanel(fileEditor, status)
        setupPluginSuggestion(panel)

        panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
            FUSEventSource.EDITOR.logIgnoreExtension(project)
            checker.dismissSuggestion()
            EditorNotifications.getInstance(project).updateAllNotifications()
        }

        return panel
    }

    private fun setupPluginSuggestion(panel: EditorNotificationPanel) {
        panel.text = checker.suggestionText
        panel.createActionLabel(checker.suggestionActionText) {
            FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
            installAndEnable(project, pluginIds.map(PluginId::getId).toSet(), true) {
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
            if (checker.requiresIdeRestart) {
                PluginManagerConfigurable.shutdownOrRestartApp()
            }
        }
    }
}

private class PluginEnablementChecker(
    val notebookLanguages: Set<String>?,
    private val dismissedKey: String,
    val suggestionText: String,
    val suggestionActionText: String,
    val requiresIdeRestart: Boolean,
    val pluginIds: List<String>,
) {
    fun isSuggestionDismissed(): Boolean = PropertiesComponent.getInstance().isTrueValue(dismissedKey)
    fun dismissSuggestion(): Unit = PropertiesComponent.getInstance().setValue(dismissedKey, true)
    fun pluginsInstalledAndEnabled(): Boolean {
        return pluginIds.all { pluginIdStr ->
            val pluginId = PluginId.getId(pluginIdStr)
            PluginManager.isPluginInstalled(pluginId) && !PluginManagerCore.isDisabled(pluginId)
        }
    }
}
