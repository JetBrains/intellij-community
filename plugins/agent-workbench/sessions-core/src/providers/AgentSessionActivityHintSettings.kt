// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.util.SystemProperties

const val AGENT_SESSION_OPTIMISTIC_ACTIVITY_HINTS_PROPERTY: String = "agent.workbench.optimistic.activity.hints"

object AgentSessionActivityHintSettings {
  fun isOptimisticActivityHintsEnabled(): Boolean {
    // TODO(IJPL-244497): Remove this temporary gate after optimistic activity hints are deleted or redesigned as an overlay.
    return SystemProperties.getBooleanProperty(AGENT_SESSION_OPTIMISTIC_ACTIVITY_HINTS_PROPERTY, false)
  }
}
