// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.intellij.DynamicBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup.Companion.createIdWithTitle
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.SystemInfoRt

class I18nMnemonicsSettingsMigrator : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    if (!SystemInfoRt.isMac || !DynamicBundle.LanguageBundleEP.EP_NAME.hasAnyExtensions()) return
    if (!PropertiesComponent.getInstance(project).getBoolean(I18N_MNEMONICS_SETTINGS, true)) return

    var controls = false
    if (!UISettings.instance.disableMnemonicsInControls) {
      UISettings.instance.disableMnemonicsInControls = true
      controls = true
    }
    var menus = false
    if (!UISettings.instance.disableMnemonics) {
      UISettings.instance.disableMnemonics = true
      menus = true
    }

    if (!controls && !menus) return

    val notification = Notification(
      createIdWithTitle("i18n Settings Changed", IdeBundle.message("notification.group.i18n.disable.mnemonics")),
      IdeBundle.message("notification.title.i18n.language.plugin.disable.mnemonics"),
      IdeBundle.message("notification.content.i18n.mnemonics.settings", if (menus) {0} else {1}, if (menus && controls) {0} else {1}, if (controls) {0} else {1}),
      NotificationType.INFORMATION)

    PropertiesComponent.getInstance(project).setValue(I18N_MNEMONICS_SETTINGS, false)

    notification.notify(project)
  }

  companion object {
    const val I18N_MNEMONICS_SETTINGS: String = "I18N_MNEMONICS_SETTINGS"
  }
}