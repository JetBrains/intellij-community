// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates.settings

import com.intellij.codeInsight.template.impl.TemplateExpandShortcutPanel
import com.intellij.compose.ide.plugin.resources.settings.ComposeResourcesSettingsContributor
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.options.UnnamedConfigurable
import javax.swing.JComponent

internal class ComposeResourcesEmmetLikeSettingsContributor : ComposeResourcesSettingsContributor {
  override val displayName get() = ComposeIdeBundle.message("compose.resources.emmet.settings.section.title")

  override fun createConfigurable(): UnnamedConfigurable = ExpandShortcutConfigurable()
}

private class ExpandShortcutConfigurable : UnnamedConfigurable {
  private var shortcutPanel: TemplateExpandShortcutPanel? = null
  private val settings get() = ComposeResourcesEmmetLikeSettings.getInstance()

  override fun createComponent(): JComponent {
    val panel = TemplateExpandShortcutPanel(ComposeIdeBundle.message("compose.resources.emmet.settings.expand.shortcut"))
    shortcutPanel = panel
    return panel.panel
  }

  override fun isModified(): Boolean =
    shortcutPanel?.let { settings.expandShortcut != it.selectedChar.code } ?: false

  override fun reset() {
    shortcutPanel?.selectedChar = settings.expandShortcut.toChar()
  }

  override fun apply() {
    shortcutPanel?.let { panel ->
      settings.expandShortcut = panel.selectedChar.code
    }
  }

  override fun disposeUIResources() {
    shortcutPanel = null
  }
}
