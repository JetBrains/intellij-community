// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.baseEvents.FusBasedCounterUserActivity
import com.intellij.ae.database.baseEvents.fus.FusEventCatcher
import com.intellij.ae.database.baseEvents.fus.FusEventDefinitionBuilder

object PluginsInstalledUserActivity : FusBasedCounterUserActivity() {
  internal class Factory: FusEventCatcher.Factory {
    override fun getInstance(): FusEventCatcher = catcher
  }

  override fun define(): FusEventDefinitionBuilder {
    return definition("plugin.installed") {
      event("plugin.manager", "plugin.installation.finished") {}
    }
  }
}