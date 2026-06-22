// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import tools.jackson.core.json.JsonFactory
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.util.SystemProperties
import java.nio.file.Path

internal class JunieSessionCostLoader(
  sessionsRootPathProvider: () -> Path = ::defaultJunieSessionsRootPath,
  jsonFactory: JsonFactory = JsonFactory(),
) {
  private val analyzer = JunieSessionEventsAnalyzer(
    sessionsRootPathProvider = sessionsRootPathProvider,
    jsonFactory = jsonFactory,
  )

  fun loadCost(sessionId: String): AgentSessionCost? {
    return analyzer.loadCost(sessionId)
  }
}

internal fun defaultJunieSessionsRootPath(): Path {
  return Path.of(SystemProperties.getUserHome(), ".junie", "sessions")
}
