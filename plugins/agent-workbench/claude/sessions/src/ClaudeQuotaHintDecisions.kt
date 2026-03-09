// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

internal fun shouldAcknowledgeClaudeQuotaHint(eligible: Boolean, acknowledged: Boolean, widgetEnabled: Boolean): Boolean {
  return eligible && !acknowledged && widgetEnabled
}

internal fun shouldShowClaudeQuotaHint(eligible: Boolean, acknowledged: Boolean, widgetEnabled: Boolean): Boolean {
  return eligible && !acknowledged && !widgetEnabled
}
