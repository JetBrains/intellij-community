package org.jetbrains.intellij.plugins.journey.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry

/**
 * From any place ask `Registry.is("ide.journey.enabled")` to check whether the journey is active
 */
internal class JourneyToggleAction : ToggleAction(), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return Registry.Companion.`is`("ide.journey.enabled")
  }

  override fun setSelected(e: AnActionEvent, isEnabled: Boolean) {
    Registry.Companion.get("ide.journey.enabled").setValue(isEnabled)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT // TODO: BGT
  }
}