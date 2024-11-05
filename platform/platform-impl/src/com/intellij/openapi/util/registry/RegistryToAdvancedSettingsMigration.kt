// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.util.registry

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.impl.TabCharacterPaintMode
import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun migrateRegistryToAdvSettings() {
  val propertyName = "registry.to.advanced.settings.migration.build"
  val propertyManager = serviceAsync<PropertiesComponent>()
  val lastMigratedVersion = propertyManager.getValue(propertyName)
  val currentBuild = ApplicationInfo.getInstance().build
  val currentVersion = currentBuild.asString()
  if (currentVersion == lastMigratedVersion && !currentBuild.isSnapshot) {
    return
  }

  val userProperties = Registry.getInstance().getStoredProperties()
  for (setting in AdvancedSettingBean.EP_NAME.extensionList) {
    when (setting.id) {
      "editor.tab.painting" -> migrateEditorTabPainting(userProperties, setting)
      "vcs.process.ignored" -> migrateVcsIgnoreProcessing(userProperties, setting)
      "ide.ui.native.file.chooser" -> migrateNativeChooser(userProperties, setting)
      else -> {
        val userProperty = userProperties[setting.id]?.value ?: continue
        try {
          AdvancedSettings.getInstance().setSetting(setting.id, setting.valueFromString(userProperty), setting.type())
          userProperties.remove(setting.id)
        }
        catch (_: IllegalArgumentException) { }
      }
    }
  }
  propertyManager.setValue(propertyName, currentVersion)
}

private fun migrateEditorTabPainting(userProperties: MutableMap<String, ValueWithSource>, setting: AdvancedSettingBean) {
  val mode = if (userProperties["editor.old.tab.painting"]?.value == "true") {
    userProperties.remove("editor.old.tab.painting")
    TabCharacterPaintMode.LONG_ARROW
  }
  else if (userProperties["editor.arrow.tab.painting"]?.value == "true") {
    userProperties.remove("editor.arrow.tab.painting")
    TabCharacterPaintMode.ARROW
  }
  else {
    return
  }
  AdvancedSettings.getInstance().setSetting(setting.id, mode, setting.type())
}

private fun migrateVcsIgnoreProcessing(userProperties: MutableMap<String, ValueWithSource>, setting: AdvancedSettingBean) {
  if (userProperties["git.process.ignored"]?.value == "false") {
    userProperties.remove("git.process.ignored")
  }
  else if (userProperties["hg4idea.process.ignored"]?.value == "false") {
    userProperties.remove("hg4idea.process.ignored")
  }
  else if (userProperties["p4.process.ignored"]?.value == "false") {
    userProperties.remove("p4.process.ignored")
  }
  else {
    return
  }
  AdvancedSettings.getInstance().setSetting(setting.id, false, setting.type())
}

private fun migrateNativeChooser(userProperties: MutableMap<String, ValueWithSource>, setting: AdvancedSettingBean) {
  val enabled = when {
    SystemInfo.isWindows -> userProperties.get("ide.win.file.chooser.native")?.value ?: System.getProperty("ide.win.file.chooser.native")
    SystemInfo.isMac -> userProperties.get("ide.mac.file.chooser.native")?.value ?: System.getProperty("ide.mac.file.chooser.native") ?: "true"
    else -> null
  } ?: return
  userProperties.remove("ide.win.file.chooser.native")
  userProperties.remove("ide.mac.file.chooser.native")
  AdvancedSettings.getInstance().setSetting(setting.id, enabled.toBoolean(), setting.type())
}
