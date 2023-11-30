// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.baseEvents.FusBasedCounterUserActivity


object AltEnterActionInvocation : FusBasedCounterUserActivity() {
  internal class Factory: FusBasedCounterUserActivity.Factory {
    override fun getInstance(): FusBasedCounterUserActivity = AltEnterActionInvocation
  }

  override fun define(): FusEventDefinitionBuilder {
    return definition("alt.enter.invocation") {
      event("actions", "action.finished") {
        field("input_event", "Alt+Enter")
      }
    }
  }
}