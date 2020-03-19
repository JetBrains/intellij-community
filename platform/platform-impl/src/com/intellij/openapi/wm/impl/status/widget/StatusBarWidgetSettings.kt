// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel

@Service
@State(name = "StatusBar", storages = [
  Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED, deprecated = true),
  Storage(value = "ide.general.xml")
])
class StatusBarWidgetSettings : SimplePersistentStateComponent<StatusBarState>(StatusBarState()) {
  fun isEnabled(factory: StatusBarWidgetFactory): Boolean {
    return state.widgets[factory.id] ?: factory.isEnabledByDefault
  }

  override fun loadState(state: StatusBarState) {
    super.loadState(state)
    migrateShowMemoryIndicatorSettings()
  }

  override fun noStateLoaded() {
    super.noStateLoaded()
    migrateShowMemoryIndicatorSettings()
  }

  private fun migrateShowMemoryIndicatorSettings() {
    if (UISettings.instance.showMemoryIndicator) {
      UISettings.instance.showMemoryIndicator = false
      state.widgets[MemoryUsagePanel.WIDGET_ID] = true
    }
  }

  fun setEnabled(factory: StatusBarWidgetFactory, newValue: Boolean) {
    if (factory.isEnabledByDefault == newValue) {
      state.widgets.remove(factory.id)
    }
    else {
      state.widgets[factory.id] = newValue
    }
  }
}

class StatusBarState : BaseState() {
  var widgets by map<String, Boolean>()
}
