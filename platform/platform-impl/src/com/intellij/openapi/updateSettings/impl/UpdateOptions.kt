// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ReportValue
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.OptionTag

class UpdateOptions : BaseState() {
  @get:CollectionBean
  val pluginHosts: MutableList<String> by list()

  @get:CollectionBean
  val ignoredBuildNumbers: MutableList<String> by list()

  @get:OptionTag("CHECK_NEEDED")
  var isCheckNeeded: Boolean by property(true)

  @get:OptionTag("PLUGINS_CHECK_NEEDED")
  var isPluginsCheckNeeded: Boolean by property(true)

  @get:OptionTag("SHOW_WHATS_NEW_EDITOR")
  var isShowWhatsNewEditor: Boolean by property(true)

  @get:OptionTag("WHATS_NEW_SHOWN_FOR")
  var whatsNewShownFor: Int by property(0)

  @get:OptionTag("LAST_TIME_CHECKED")
  var lastTimeChecked: Long by property(0L)

  @get:OptionTag("LAST_BUILD_CHECKED")
  var lastBuildChecked: String? by string()

  @get:OptionTag("UPDATE_CHANNEL_TYPE")
  @get:ReportValue(possibleValues = ["eap", "milestone", "beta", "release"])
  var updateChannelType: String? by string(ChannelStatus.RELEASE.code)

  @get:OptionTag("THIRD_PARTY_PLUGINS_ALLOWED")
  var isThirdPartyPluginsAllowed: Boolean by property(false)

  @get:OptionTag("OBSOLETE_CUSTOM_REPOSITORIES_CLEAN_NEEDED")
  var isObsoleteCustomRepositoriesCleanNeeded: Boolean by property(true)
}
