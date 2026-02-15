// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.plugin

import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

@Suite
@SelectPackages(
  "com.intellij.agent.workbench.chat",
  "com.intellij.agent.workbench.claude.sessions",
  "com.intellij.agent.workbench.codex.sessions",
  "com.intellij.agent.workbench.sessions",
)
class AgentWorkbenchAllTestsSuite
