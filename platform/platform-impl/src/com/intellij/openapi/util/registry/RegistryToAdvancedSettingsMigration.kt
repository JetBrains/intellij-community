// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class RegistryToAdvancedSettingsMigration : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    val propertyName = "registry.to.advanced.settings.migration.build"
    val lastMigratedVersion = PropertiesComponent.getInstance().getValue(propertyName)
    val currentVersion = ApplicationInfo.getInstance().build.asString()
    if (currentVersion != lastMigratedVersion) {
      val userProperties = Registry.getInstance().userProperties
      for (setting in AdvancedSettingBean.EP_NAME.extensions) {
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
}
