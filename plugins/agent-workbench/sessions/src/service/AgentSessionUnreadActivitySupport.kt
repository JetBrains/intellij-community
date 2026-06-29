// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionThread

internal fun AgentSessionThread.hasUnreadActivitySignal(): Boolean {
  return activityReport.rowActivity == AgentThreadActivity.UNREAD || activityReport.chromeActivity == AgentThreadActivity.UNREAD
}
