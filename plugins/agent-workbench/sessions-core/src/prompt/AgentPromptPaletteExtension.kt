// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

/**
 * Extension point that allows contributing custom tabs to the prompt palette popup.
 *
 * When context items match (e.g., VCS Revisions), a dynamic tab is added with the given title.
 * The extension can provide initial prompt text, a custom submit action, and a footer hint.
 */
interface AgentPromptPaletteExtension {
  /**
   * Returns `true` if this extension's tab should appear for the given context items.
   */
  fun matches(contextItems: List<AgentPromptContextItem>): Boolean

  /**
   * Returns the tab title displayed in the tabbed pane header.
   */
  fun getTabTitle(): @Nls String

  /**
   * Returns initial text to pre-fill in the prompt area when the tab is selected, or `null` to leave it empty.
   */
  fun getInitialPromptText(project: Project): @Nls String?

  /**
   * Returns the action ID to invoke on submit instead of the normal launch flow,
   * or `null` to use the default behavior.
   */
  fun getSubmitActionId(): String?

  /**
   * Returns a custom footer hint to display when this tab is active,
   * or `null` to use the default hint.
   */
  fun getFooterHint(): @Nls String?

  /**
   * Returns `true` if this extension's tab should be auto-selected when the popup
   * is invoked with the "prefer extensions" shortcut and this extension [matches] the context.
   * When multiple extensions return `true`, the first one wins.
   */
  fun shouldAutoSelect(contextItems: List<AgentPromptContextItem>): Boolean = matches(contextItems)
}

object AgentPromptPaletteExtensions {
  private val EP_NAME: ExtensionPointName<AgentPromptPaletteExtension> =
    ExtensionPointName("com.intellij.agent.workbench.promptPaletteExtension")

  fun allExtensions(): List<AgentPromptPaletteExtension> = EP_NAME.extensionList
}
