// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.github.ultimatePromo

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.jetbrains.plugins.github.extensions.isGithubActionsFile
import org.jetbrains.plugins.github.i18n.GithubBundle

private const val GITHUB_ULTIMATE_SUGGESTION_DISMISSED_KEY: String = "promo.github.actions.suggestion.dismissed"
private const val GITHUB_PLUGIN_ID = "org.jetbrains.plugins.github"

internal class GitHubUltimateSuggestionProvider : PluginSuggestionProvider {

  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (isPluginSuggestionDismissed() || tryUltimateIsDisabled()) return null
    if (!isGithubActionsFile(file)) return null
    val thisProductCode = ApplicationInfo.getInstance().build.productCode
    val suggestedIdeCode = PluginAdvertiserService.getSuggestedCommercialIdeCode(thisProductCode)
    val suggestedCommercialIde = PluginAdvertiserService.getIde(suggestedIdeCode) ?: return null
    return UltimateForGitHubActionsSuggestion(project, suggestedCommercialIde)
  }

  private fun isPluginSuggestionDismissed(): Boolean {
    return PropertiesComponent.getInstance().isTrueValue(GITHUB_ULTIMATE_SUGGESTION_DISMISSED_KEY)
  }

  internal class UltimateForGitHubActionsSuggestion(val project: Project, val suggestedCommercialIde: SuggestedIde) : PluginSuggestion {
    override val pluginIds: List<String> = listOf(GITHUB_PLUGIN_ID)

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Promo)
      panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", GithubBundle.message("github.actions.file.promo.label"), suggestedCommercialIde.name)
      panel.createTryUltimateActionLabel(suggestedCommercialIde, project, PluginId.getId(pluginIds[0]))
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
        FUSEventSource.EDITOR.logIgnoreExtension(project)
        dismissPluginSuggestion()
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
      return panel
    }

    private fun dismissPluginSuggestion() {
      PropertiesComponent.getInstance().setValue(GITHUB_ULTIMATE_SUGGESTION_DISMISSED_KEY, true)
    }
  }
}