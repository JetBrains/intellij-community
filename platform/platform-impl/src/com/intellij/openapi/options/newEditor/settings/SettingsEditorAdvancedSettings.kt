// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor.settings

import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.options.newEditor.SettingsEditor
import com.intellij.openapi.project.ProjectManager

object SettingsEditorAdvancedSettings : AdvancedSettingsChangeListener {
  private fun setDebugLogging() {
    LogLevelConfigurationManager.getInstance().addCategories(listOf(
      LogCategory(SettingsEditor::class.java.name, DebugLogLevel.DEBUG)
    ))

  }
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (!id.startsWith("ide.ui.non.modal.settings.window" ) || !AdvancedSettings.getBoolean("ide.ui.non.modal.settings.window")) {
      return
    }

    setDebugLogging()
    if (id == "ide.ui.non.modal.settings.window.instant.apply") {
      ApplicationManager.getApplication().invokeLater {
        ProjectManager.getInstance().openProjects.forEach { project ->
          val existingSettingsFile = SettingsVirtualFileHolder.getInstance(project).invalidate()
          if (existingSettingsFile != null) {
            FileEditorManagerEx.getInstanceEx(project).closeFile(existingSettingsFile)
          }
        }
      }
    }
  }

  val instantSettingsApply: Boolean
    get() = AdvancedSettings.getBoolean("ide.ui.non.modal.settings.window")
            && AdvancedSettings.getBoolean("ide.ui.non.modal.settings.window.instant.apply")
}