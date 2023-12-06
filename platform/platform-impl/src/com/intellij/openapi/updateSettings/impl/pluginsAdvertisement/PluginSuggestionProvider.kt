// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

@ApiStatus.Internal
interface PluginSuggestionProvider {
  fun getSuggestion(project: Project, file: VirtualFile): Function<FileEditor, EditorNotificationPanel?>?
}

internal class DefaultPluginSuggestion(
  private val project: Project,
  private val pluginIds: List<String>,
  private val pluginName: String,
  private val fileLabel: String,
  private val suggestionDismissKey: String
) : Function<FileEditor, EditorNotificationPanel?> {
  override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
    val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)

    panel.text = IdeBundle.message("plugins.advertiser.plugins.found", fileLabel)

    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugin.name", pluginName)) {
      FUSEventSource.EDITOR.logInstallPlugins(pluginIds, project)
      installAndEnable(project, pluginIds.map(PluginId::getId).toSet(), true) {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }

    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
      FUSEventSource.EDITOR.logIgnoreExtension(project)
      PropertiesComponent.getInstance().setValue(suggestionDismissKey, true)
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    return panel
  }
}

fun buildSuggestionIfNeeded(project: Project,
                            pluginIds: List<String>,
                            pluginName: String,
                            fileLabel: String,
                            suggestionDismissKey: String): Function<FileEditor, EditorNotificationPanel?>? {
  if (PropertiesComponent.getInstance().isTrueValue(suggestionDismissKey)) return null

  val requiredPluginIds = pluginIds.filter { id ->
    val requiredPluginId = PluginId.getId(id)
    !PluginManager.isPluginInstalled(requiredPluginId)
  }

  if (requiredPluginIds.isEmpty()) return null

  return DefaultPluginSuggestion(project,
                                 pluginIds = requiredPluginIds,
                                 pluginName = pluginName,
                                 fileLabel = fileLabel,
                                 suggestionDismissKey = suggestionDismissKey)
}

fun buildSuggestionIfNeeded(project: Project,
                            pluginId: String,
                            pluginName: String,
                            fileLabel: String,
                            suggestionDismissKey: String): Function<FileEditor, EditorNotificationPanel?>? {
  return buildSuggestionIfNeeded(project, listOf(pluginId), pluginName, fileLabel, suggestionDismissKey)
}
