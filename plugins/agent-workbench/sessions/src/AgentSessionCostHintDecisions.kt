// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

fun shouldAcknowledgeAgentSessionCostHint(eligible: Boolean, acknowledged: Boolean, settingEnabled: Boolean): Boolean {
  return eligible && !acknowledged && settingEnabled
}

fun shouldShowAgentSessionCostHint(eligible: Boolean, acknowledged: Boolean, settingEnabled: Boolean): Boolean {
  return eligible && !acknowledged && !settingEnabled
}
