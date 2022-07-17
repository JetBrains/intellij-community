// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ShowUsagesSettings", storages = [(Storage("usageView.xml"))], category = SettingsCategory.UI)
class ShowUsagesSettings : PersistentStateComponent<ShowUsageViewSettings> {
  companion object {
    @JvmStatic
    val instance: ShowUsagesSettings
      get() = ApplicationManager.getApplication().getService(ShowUsagesSettings::class.java)
  }

  private var state = ShowUsageViewSettings()

  override fun getState(): ShowUsageViewSettings = state

  override fun loadState(state: ShowUsageViewSettings) {
    this.state = state
  }

  fun applyUsageViewSettings(otherState: UsageViewSettings) {
    state.copyFrom(otherState)
  }
}

class ShowUsageViewSettings : UsageViewSettings(false, false, false, false, false) {

  override var isGroupByUsageType: Boolean
    get() = false
    set(_) {}

  override var isGroupByModule: Boolean
    get() = false
    set(_) {}

  override var isGroupByPackage: Boolean
    get() = false
    set(_) {}

  override var isGroupByDirectoryStructure: Boolean
    get() = false
    set(_) {}

  override var isGroupByScope: Boolean
    get() = false
    set(_) {}
}
