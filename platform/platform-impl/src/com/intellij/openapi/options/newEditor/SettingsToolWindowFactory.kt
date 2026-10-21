// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.toolWindow.DefaultToolWindowLayoutBuilder
import com.intellij.toolWindow.DefaultToolWindowLayoutExtension
import com.intellij.ui.content.ContentFactory

internal const val SETTINGS_TOOL_WINDOW_ID: String = "Settings"

/** Shows the Settings in the Settings tool window and in the Settings editor tab instead of a dialog. */
internal const val SETTINGS_TOOL_WINDOW_REGISTRY_KEY: String = "ide.settings.tool.window.enabled"

/**
 * Creates the Settings tool window. It shows the search field and the settings tree of [SettingsToolWindowManager].
 *
 * The stripe button stays below every other button of the left toolbar, and the tool window docks on the left.
 * A project that opens with the registry key [SETTINGS_TOOL_WINDOW_REGISTRY_KEY] off has no Settings tool window,
 * so nothing of it shows in the menus. Turning the key off later hides the stripe button at once.
 */
internal class SettingsToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    return RegistryManager.getInstance().`is`(SETTINGS_TOOL_WINDOW_REGISTRY_KEY)
  }

  override fun init(toolWindow: ToolWindow) {
    (toolWindow as? ToolWindowImpl)?.isInLeftToolbarFooter = true
    RegistryManager.getInstance().get(SETTINGS_TOOL_WINDOW_REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        toolWindow.isAvailable = value.asBoolean()
      }
    }, toolWindow.disposable)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val manager = SettingsToolWindowManager.getInstance(project)
    val content = ContentFactory.getInstance().createContent(manager.sidebar, null, false)
    content.isCloseable = false
    content.setPreferredFocusedComponent { manager.getPreferredFocusedSidebarComponent() }
    toolWindow.contentManager.addContent(content)
    manager.createEditorInBackground()
  }
}

/** Puts the Settings tool window on the left in the New UI, so that it has a stripe button by default. */
internal class SettingsToolWindowLayoutExtension : DefaultToolWindowLayoutExtension {
  override fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder) {
  }

  override fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder) {
    // with the key off, the project has no Settings tool window, so a layout must get no entry for it
    if (!RegistryManager.getInstance().`is`(SETTINGS_TOOL_WINDOW_REGISTRY_KEY)) {
      return
    }
    builder.left.addOrUpdate(SETTINGS_TOOL_WINDOW_ID) {
      weight = 0.2f
      seedIntoExistingLayout()
    }
  }
}
