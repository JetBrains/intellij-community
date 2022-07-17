// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.impl.TabCharacterPaintMode
import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

private class RegistryToAdvancedSettingsMigration : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    val propertyName = "registry.to.advanced.settings.migration.build"
    val lastMigratedVersion = PropertiesComponent.getInstance().getValue(propertyName)
    val currentVersion = ApplicationInfo.getInstance().build.asString()
    if (currentVersion != lastMigratedVersion) {
      val userProperties = Registry.getInstance().userProperties
      for (setting in AdvancedSettingBean.EP_NAME.extensions) {
        if (setting.id == "editor.tab.painting") {
          migrateEditorTabPainting(userProperties, setting)
          continue
        }

        if (setting.id == "vcs.process.ignored") {
          migrateVcsIgnoreProcessing(userProperties, setting)
          continue
        }

        val userProperty = userProperties[setting.id] ?: continue
        try {
          AdvancedSettings.getInstance().setSetting(setting.id, setting.valueFromString(userProperty), setting.type())
          userProperties.remove(setting.id)
        }
        catch (e: IllegalArgumentException) {
          continue
        }
      }
      PropertiesComponent.getInstance().setValue(propertyName, currentVersion)
    }
  }

  private fun migrateEditorTabPainting(userProperties: MutableMap<String, String>, setting: AdvancedSettingBean) {
    val mode = if (userProperties["editor.old.tab.painting"] == "true") {
      userProperties.remove("editor.old.tab.painting")
      TabCharacterPaintMode.LONG_ARROW
    }
    else if (userProperties["editor.arrow.tab.painting"] == "true") {
      userProperties.remove("editor.arrow.tab.painting")
      TabCharacterPaintMode.ARROW
    }
    else {
      return
    }
    AdvancedSettings.getInstance().setSetting(setting.id, mode, setting.type())
  }

  private fun migrateVcsIgnoreProcessing(userProperties: MutableMap<String, String>, setting: AdvancedSettingBean) {
    if (userProperties["git.process.ignored"] == "false") {
      userProperties.remove("git.process.ignored")
    }
    else if (userProperties["hg4idea.process.ignored"] == "false") {
      userProperties.remove("hg4idea.process.ignored")
    }
    else if (userProperties["p4.process.ignored"] == "false") {
      userProperties.remove("p4.process.ignored")
    }
    else {
      return
    }
    AdvancedSettings.getInstance().setSetting(setting.id, false, setting.type())
  }
}
