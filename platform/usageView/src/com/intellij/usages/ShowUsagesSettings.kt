/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ShowUsagesSettings", storages = arrayOf(Storage("usageView.xml")))
class ShowUsagesSettings : PersistentStateComponent<ShowUsageViewSettings> {
  companion object {
    @JvmStatic
    val instance: ShowUsagesSettings
      get() = ServiceManager.getService(ShowUsagesSettings::class.java)
  }

  private var state = ShowUsageViewSettings()

  override fun getState() = state

  override fun loadState(state: ShowUsageViewSettings) {
    this.state = state
  }

  fun applyUsageViewSettings(otherState: UsageViewSettings) {
    state.copyFrom(otherState)
  }
}

class ShowUsageViewSettings : UsageViewSettings(false, false, false, false, false)
