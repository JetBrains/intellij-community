// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp.settings

import com.intellij.openapi.components.*
import com.intellij.util.application
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.builtInHelp.BuiltInHelpBundle
import org.jetbrains.annotations.NonNls

const val defaultBaseUrl = "https://www.jetbrains.com/"

@Service(Service.Level.APP)
@State(name = "HelpPluginSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)])
class HelpPluginSettings : PersistentStateComponent<HelpPluginSettings> {

  var openHelpFromWeb: Boolean = true
  var useBrowser: @NonNls String = BuiltInHelpBundle.message("use.default.browser")
  var openHelpBaseUrl: @NonNls String = defaultBaseUrl

  override

  fun getState(): HelpPluginSettings {
    return this
  }

  override fun loadState(state: HelpPluginSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): HelpPluginSettings {
      return application.getService(HelpPluginSettings::class.java)
    }
  }
}