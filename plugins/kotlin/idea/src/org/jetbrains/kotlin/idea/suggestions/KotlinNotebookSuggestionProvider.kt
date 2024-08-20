// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.suggestions

import com.google.gson.JsonParser
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.isCommunityIde
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

private const val KOTLIN_NOTEBOOKS_PRIMARY_PLUGIN_ID = "org.jetbrains.plugins.kotlin.jupyter"
private val KOTLIN_NOTEBOOKS_ALL_PLUGIN_IDS = listOf(
    KOTLIN_NOTEBOOKS_PRIMARY_PLUGIN_ID,
    "intellij.jupyter",
    "com.intellij.notebooks.core",
)
private const val KOTLIN_NOTEBOOKS_PLUGIN_NAME: String = "Kotlin Notebook"
private const val KOTLIN_NOTEBOOKS_PLUGIN_FILES: String = "Kotlin Notebook (*.ipynb)"
private const val KOTLIN_NOTEBOOKS_PLUGIN_SUGGESTION_DISMISSED_KEY: String = "notebook.kotlin.suggestion.dismissed"

/**
 * Suggestion provider for the Kotlin Notebooks plugin. It supports Jupyter Notebook files (*.ipynb) that
 * are using the Kotlin kernel.
 */
internal class KotlinNotebookSuggestionProvider : PluginSuggestionProvider {

    private fun isPluginSuggestionDismissed(): Boolean {
        return PropertiesComponent.getInstance().isTrueValue(KOTLIN_NOTEBOOKS_PLUGIN_SUGGESTION_DISMISSED_KEY)
    }

    private fun requiredPluginsInstalled(): Boolean {
        return KOTLIN_NOTEBOOKS_ALL_PLUGIN_IDS.all { pluginIdStr ->
            val pluginId = PluginId.getId(pluginIdStr)
            PluginManager.isPluginInstalled(pluginId) && !PluginManagerCore.isDisabled(pluginId)
        }
    }

    override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
        // Copy the file extension from `com.intellij.jupyter.core.jupyter.JupyterFileType` to avoid
        // having a dependency on the Jupyter module.
        if (file.extension != "ipynb") {
            return null
        }

        if (requiredPluginsInstalled()) {
            return null
        }

        if (isPluginSuggestionDismissed() || tryUltimateIsDisabled()) {
            return null
        }

        // We should only consider notebooks configured to use the Kotlin kernel.
        // Any error in the JSON file or format will be ignored as it is handled as part
        // of opening the file in the editor.
        try {
            val json = JsonParser.parseString(file.readText())
            val notebookLanguage = json.asJsonObject["metadata"]
                ?.asJsonObject?.get("language_info")
                ?.asJsonObject?.get("name")
                ?.asString
            if (!notebookLanguage.equals("kotlin", ignoreCase = true)) {
                return null
            }
        } catch (ex: Throwable) {
            return null
        }

        return KotlinNotebookPluginSuggestion(project, isCommunityIde())
    }
}

/**
 * Show a promotion for either IDE or Plugin depending on the type defined by [isCommunity].
 *
 * In community editions (Android Studio/IJ Community/PyCharm Community), this suggestion will
 * show a promotion for IJ Ultimate. In Ultimate, this suggestion will instead suggest
 * installing the Kotlin Notebook plugin.
 */
class KotlinNotebookPluginSuggestion(private val project: Project, private val isCommunity: Boolean) : PluginSuggestion {

    // We need to manually list all dependencies of the Notebook plugin.
    // See IJPL-149727
    override val pluginIds: List<String> = listOf(KOTLIN_NOTEBOOKS_PRIMARY_PLUGIN_ID)

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
        val status = if (isCommunity) EditorNotificationPanel.Status.Promo else EditorNotificationPanel.Status.Info
        val panel = EditorNotificationPanel(fileEditor, status)
        if (isCommunity) {
            setupCommercialIdeSuggestion(panel)
        } else {
            setupPluginSuggestion(panel)
        }

        panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
            FUSEventSource.EDITOR.logIgnoreExtension(project)
            dismissPluginSuggestion()
            EditorNotifications.getInstance(project).updateAllNotifications()
        }

        return panel
    }

    private fun dismissPluginSuggestion() {
        PropertiesComponent.getInstance().setValue(KOTLIN_NOTEBOOKS_PLUGIN_SUGGESTION_DISMISSED_KEY, true)
    }

    private fun setupCommercialIdeSuggestion(panel: EditorNotificationPanel) {
        val ultimateIde = PluginAdvertiserService.getIde("IU") ?: error("Could not find IntelliJ Ultimate reference")
        panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", KOTLIN_NOTEBOOKS_PLUGIN_FILES, ultimateIde.name)
        panel.createTryUltimateActionLabel(ultimateIde, project, PluginId.getId(KOTLIN_NOTEBOOKS_PRIMARY_PLUGIN_ID))
    }

    private fun setupPluginSuggestion(panel: EditorNotificationPanel) {
        panel.text = IdeBundle.message("plugins.advertiser.plugins.found", KOTLIN_NOTEBOOKS_PLUGIN_FILES)
        panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugin.name", KOTLIN_NOTEBOOKS_PLUGIN_NAME)) {
            FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
            installAndEnable(project, pluginIds.map(PluginId::getId).toSet(), true) {
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
    }
}
