// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.baseEvents.FusBasedCounterUserActivity

object PluginsInstalledUserActivity : FusBasedCounterUserActivity() {
  internal class Factory: FusBasedCounterUserActivity.Factory {
    override fun getInstance(): FusBasedCounterUserActivity = PluginsInstalledUserActivity
  }

  override fun define(): FusEventDefinitionBuilder {
    return definition("plugin.installed") {
      event("plugin.manager", "plugin.installation.finished") {}
    }
  }
}