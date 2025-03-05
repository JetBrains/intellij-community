// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.suggestions

import com.google.gson.JsonParser
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.isCommunityIde
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.tryUltimateIsDisabled
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText

/**
 * Suggestion provider for the Notebooks-related plugins (Kotlin Notebook or Jupyter + Notebook Files).
 */
internal class JupyterNotebookSuggestionProvider : PluginSuggestionProvider {

    companion object {
        const val KOTLIN_NOTEBOOKS_PRIMARY_PLUGIN_ID = "org.jetbrains.plugins.kotlin.jupyter"
        const val JUPYTER_PLUGIN_ID = "intellij.jupyter"
        const val NOTEBOOK_FILES_PLUGIN_ID = "com.intellij.notebooks.core"

        const val KOTLIN_NOTEBOOKS_PLUGIN_NAME: String = "Kotlin Notebook"
        const val KOTLIN_NOTEBOOKS_PLUGIN_FILES: String = "Kotlin Notebook (*.ipynb)"
        const val KOTLIN_NOTEBOOKS_PLUGIN_SUGGESTION_DISMISSED_KEY: String = "notebook.kotlin.suggestion.dismissed"

        const val JUPYTER_NOTEBOOKS_PLUGIN_FILES: String = "*.ipynb"
        const val JUPYTER_PLUGIN_SUGGESTION_DISMISSED_KEY: String = "jupyter.suggestion.dismissed"
        const val NOTEBOOK_FILES_PLUGIN_SUGGESTION_DISMISSED_KEY: String = "notebook.files.suggestion.dismissed"
    }

    private fun isKotlinNotebooksPluginSuggestionDismissed(): Boolean {
        return PropertiesComponent.getInstance().isTrueValue(KOTLIN_NOTEBOOKS_PLUGIN_SUGGESTION_DISMISSED_KEY)
    }

    private fun isJupyterNotebooksPluginSuggestionDismissed(): Boolean {
        return PropertiesComponent.getInstance().isTrueValue(JUPYTER_PLUGIN_SUGGESTION_DISMISSED_KEY) && PropertiesComponent.getInstance()
            .isTrueValue(NOTEBOOK_FILES_PLUGIN_SUGGESTION_DISMISSED_KEY)
    }

    private fun requiredPluginsKotlinNotebooksInstalled(): Boolean {
        val pluginsToInstall = listOf(KOTLIN_NOTEBOOKS_PRIMARY_PLUGIN_ID, JUPYTER_PLUGIN_ID, NOTEBOOK_FILES_PLUGIN_ID)
        return pluginsToInstall.all { pluginIdStr ->
            val pluginId = PluginId.getId(pluginIdStr)
            PluginManager.isPluginInstalled(pluginId) && !PluginManagerCore.isDisabled(pluginId)
        }
    }

    private fun requiredPluginsJupyterNotebooksInstalled(): Boolean {
        val pluginsToInstall = listOf(JUPYTER_PLUGIN_ID, NOTEBOOK_FILES_PLUGIN_ID)
        return pluginsToInstall.all { pluginIdStr ->
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

        if (requiredPluginsKotlinNotebooksInstalled() || requiredPluginsJupyterNotebooksInstalled()) {
            return null
        }

        if (isKotlinNotebooksPluginSuggestionDismissed() || isJupyterNotebooksPluginSuggestionDismissed() || tryUltimateIsDisabled()) {
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
                return JupyterAndNotebookFilesPluginsSuggestion(project, isCommunityIde())
            }
        } catch (_: Throwable) {
            return null
        }

        return KotlinNotebookPluginSuggestion(project, isCommunityIde())
    }
}
