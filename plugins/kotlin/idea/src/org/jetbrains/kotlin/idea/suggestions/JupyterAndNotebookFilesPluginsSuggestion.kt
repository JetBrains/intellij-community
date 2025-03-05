// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.suggestions

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.JUPYTER_NOTEBOOKS_PLUGIN_FILES
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.JUPYTER_PLUGIN_ID
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.JUPYTER_PLUGIN_SUGGESTION_DISMISSED_KEY
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.NOTEBOOK_FILES_PLUGIN_ID
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.NOTEBOOK_FILES_PLUGIN_SUGGESTION_DISMISSED_KEY

/**
 * Show a promotion for either IDE or Plugin depending on the type defined by [isCommunity].
 *
 * In community editions (Android Studio/IJ Community/PyCharm Community), this suggestion will
 * show a promotion for IJ Ultimate. In Ultimate, this suggestion will instead suggest
 * installing the Jupyter and Notebook Files plugins.
 */
internal class JupyterAndNotebookFilesPluginsSuggestion(private val project: Project, private val isCommunity: Boolean) : PluginSuggestion {
    override val pluginIds: List<String> = listOf(JUPYTER_PLUGIN_ID, NOTEBOOK_FILES_PLUGIN_ID)

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
            dismissPluginsSuggestion()
            EditorNotifications.getInstance(project).updateAllNotifications()
        }

        return panel
    }

    private fun dismissPluginsSuggestion() {
        PropertiesComponent.getInstance().setValue(JUPYTER_PLUGIN_SUGGESTION_DISMISSED_KEY, true)
        PropertiesComponent.getInstance().setValue(NOTEBOOK_FILES_PLUGIN_SUGGESTION_DISMISSED_KEY, true)
    }

    private fun setupCommercialIdeSuggestion(panel: EditorNotificationPanel) {
        val ultimateIde = PluginAdvertiserService.getIde("IU") ?: error("Could not find IntelliJ Ultimate reference")
        panel.text =
            IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", JUPYTER_NOTEBOOKS_PLUGIN_FILES, ultimateIde.name)
        panel.createTryUltimateActionLabel(ultimateIde, project, PluginId.getId(pluginIds[0]))
    }

    private fun setupPluginSuggestion(panel: EditorNotificationPanel) {
        panel.text = IdeBundle.message("plugins.advertiser.plugins.found", JUPYTER_NOTEBOOKS_PLUGIN_FILES)
        panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugins")) {
            FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
            installAndEnable(project, pluginIds.map(PluginId::getId).toSet(), true) {
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
            PluginManagerConfigurable.shutdownOrRestartApp()
        }
    }
}