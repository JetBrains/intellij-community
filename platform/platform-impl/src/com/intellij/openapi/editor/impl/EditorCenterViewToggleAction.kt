// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry

internal class EditorCenterViewToggleAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    return Registry.`is`("editor.distraction.free.mode")
  }

  override fun setSelected(event: AnActionEvent, isSelected: Boolean) {
    Registry.get("editor.distraction.free.mode").setValue(isSelected)
    EditorFactory.getInstance().allEditors.forEach { editor ->
      (editor as? EditorImpl)?.reinitSettings()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}