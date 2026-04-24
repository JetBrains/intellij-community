// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.intellij.execution.configurations.PathEnvironmentVariableUtil

object CodexCliUtils {
  const val CODEX_COMMAND: String = "codex"

  fun findExecutable(): String? {
    return PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(CODEX_COMMAND)?.absolutePath
  }
}
