// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.OptionTag

class UpdateOptions : BaseState() {
  @get:CollectionBean
  val pluginHosts: MutableList<String> by list()

  @get:CollectionBean
  val ignoredBuildNumbers: MutableList<String> by list()

  @get:CollectionBean
  val enabledExternalComponentSources: MutableList<String> by list()

  @get:CollectionBean
  val knownExternalComponentSources: MutableList<String> by list()

  @get:CollectionBean
  val externalUpdateChannels: MutableMap<String, String> by map()

  @get:OptionTag("CHECK_NEEDED")
  var isCheckNeeded: Boolean by property(true)

  @get:OptionTag("LAST_TIME_CHECKED")
  var lastTimeChecked: Long by property(0L)

  @get:OptionTag("LAST_BUILD_CHECKED")
  var lastBuildChecked: String? by string()

  @get:OptionTag("UPDATE_CHANNEL_TYPE")
  var updateChannelType: String? by string(ChannelStatus.RELEASE.code)

  @get:OptionTag("SECURE_CONNECTION")
  var isUseSecureConnection: Boolean by property(true)

  @get:OptionTag("THIRD_PARTY_PLUGINS_ALLOWED")
  var isThirdPartyPluginsAllowed: Boolean by property(false)
}