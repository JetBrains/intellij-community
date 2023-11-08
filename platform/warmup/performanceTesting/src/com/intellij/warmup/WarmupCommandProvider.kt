// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

class WarmupCommandProvider : CommandProvider {
  override fun getCommands() : Map<String, CreateCommand> = mapOf(
    Pair(BUILD_PREFIX, CreateCommand(::CheckWarmupBuildStatusCommand)),
    Pair(GIT_LOG_PREFIX, CreateCommand(::CheckGitLogIndexedCommand)),
  )
}