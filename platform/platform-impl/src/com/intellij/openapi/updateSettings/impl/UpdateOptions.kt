// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.OptionTag

class UpdateOptions : BaseState() {
  @get:CollectionBean
  val pluginHosts by list<String>()

  @get:CollectionBean
  val ignoredBuildNumbers by list<String>()

  @get:CollectionBean
  val enabledExternalComponentSources by list<String>()

  @get:CollectionBean
  val knownExternalComponentSources by list<String>()

  @get:CollectionBean
  val externalUpdateChannels by map<String, String>()

  @get:OptionTag("CHECK_NEEDED")
  var isCheckNeeded by property(true)

  @get:OptionTag("LAST_TIME_CHECKED")
  var lastTimeChecked by property(0L)

  @get:OptionTag("LAST_BUILD_CHECKED")
  var lastBuildChecked by string()

  @get:OptionTag("UPDATE_CHANNEL_TYPE")
  var updateChannelType by string(ChannelStatus.RELEASE.code)

  @get:OptionTag("SECURE_CONNECTION")
  var isUseSecureConnection by property(true)

  @get:OptionTag("THIRD_PARTY_PLUGINS_ALLOWED")
  var isThirdPartyPluginsAllowed by property(false)
}