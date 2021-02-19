// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ReportValue
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.concurrent.TimeUnit

class UpdateOptions : BaseState() {
  @get:CollectionBean
  val pluginHosts by list<String>()

  @get:CollectionBean
  val ignoredBuildNumbers by list<String>()

  @get:OptionTag("CHECK_NEEDED")
  var isCheckNeeded by property(true)

  @get:OptionTag("PLUGINS_CHECK_NEEDED")
  var isPluginsCheckNeeded by property(true)

  @get:OptionTag("SHOW_WHATS_NEW_EDITOR")
  var isShowWhatsNewEditor by property(true)

  @get:OptionTag("LAST_TIME_CHECKED")
  var lastTimeChecked by property(0L)

  @get:OptionTag("LAST_BUILD_CHECKED")
  var lastBuildChecked by string()

  @get:OptionTag("UPDATE_CHANNEL_TYPE")
  @get:ReportValue(possibleValues = ["eap", "milestone", "beta", "release"])
  var updateChannelType by string(ChannelStatus.RELEASE.code)

  @get:OptionTag("THIRD_PARTY_PLUGINS_ALLOWED")
  var isThirdPartyPluginsAllowed by property(false)
}
