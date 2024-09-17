// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.journey

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
    return Registry.`is`("ide.journey.enabled")
  }

  override fun setSelected(e: AnActionEvent, isEnabled: Boolean) {
    Registry.get("ide.journey.enabled").setValue(isEnabled)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT // TODO: BGT
  }
}
