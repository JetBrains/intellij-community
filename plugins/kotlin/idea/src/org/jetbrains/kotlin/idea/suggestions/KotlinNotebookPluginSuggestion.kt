// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.suggestions

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.KOTLIN_NOTEBOOKS_PLUGIN_FILES
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.KOTLIN_NOTEBOOKS_PLUGIN_NAME
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.KOTLIN_NOTEBOOKS_PLUGIN_SUGGESTION_DISMISSED_KEY
import org.jetbrains.kotlin.idea.suggestions.JupyterNotebookSuggestionProvider.Companion.KOTLIN_NOTEBOOKS_PRIMARY_PLUGIN_ID

/**
 * Show a promotion for either IDE or Plugin depending on the type defined by [isCommunity].
 *
 * In community editions (Android Studio/IJ Community/PyCharm Community), this suggestion will
 * show a promotion for IJ Ultimate. In Ultimate, this suggestion will instead suggest
 * installing the Kotlin Notebook plugin.
 */
internal class KotlinNotebookPluginSuggestion(private val project: Project, private val isCommunity: Boolean) : PluginSuggestion {

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