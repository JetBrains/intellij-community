// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.plugin

import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

@Suite
@SelectPackages(
  "com.intellij.platform.ai.agent.common",
  "com.intellij.agent.workbench.thread.view",
  "com.intellij.platform.ai.agent.claude.sessions",
  "com.intellij.agent.workbench.codex.ide",
  "com.intellij.platform.ai.agent.codex.sessions",
  "com.intellij.platform.ai.agent.junie.sessions",
  "com.intellij.agent.workbench.prompt.core",
  "com.intellij.agent.workbench.prompt.ui",
  "com.intellij.agent.workbench.prompt.vcs",
  "com.intellij.agent.workbench.prompt.testrunner",
  "com.intellij.agent.workbench.sessions",
  "com.intellij.agent.workbench.sessions.launch.config.backend",
  "com.intellij.agent.workbench.vcs.merge",
)
class AgentWorkbenchAllTestsSuite
