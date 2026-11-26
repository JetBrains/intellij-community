// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.extendedToolWindowsUi.frontend

import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.wm.WindowManager

internal class ToolWindowRotateIconsSettingListener : AdvancedSettingsChangeListener {

  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id == ROTATE_ICONS_SETTING_ID) {
      for (frame in WindowManager.getInstance().allProjectFrames) {
        frame.component.repaint()
      }
    }
  }
}
