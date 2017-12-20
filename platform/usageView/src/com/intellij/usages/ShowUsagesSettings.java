/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "ShowUsagesSettings", storages = arrayOf(Storage("usageView.xml")))
class ShowUsagesSettings : PersistentStateComponent<UsageViewSettings> {
  private val myState = UsageViewSettings(false, false, false, false, false)

  override fun getState(): UsageViewSettings? {
    return myState
  }

  override fun loadState(state: UsageViewSettings) {
    XmlSerializerUtil.copyBean(state, myState)
  }

  companion object {

    val instance: ShowUsagesSettings
      get() = ServiceManager.getService(ShowUsagesSettings::class.java)
  }
}
