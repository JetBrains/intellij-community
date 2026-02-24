// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

data class AgentSessionProviderIcon(
  @JvmField val path: String,
  @JvmField val iconClass: Class<*>,
)
