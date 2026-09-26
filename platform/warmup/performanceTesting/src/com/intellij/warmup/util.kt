// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.tools.ide.performanceTesting.commands.CommandChain

const val BUILD_PREFIX = AbstractCommand.CMD_PREFIX + "checkWarmupBuild"

const val GIT_LOG_PREFIX = AbstractCommand.CMD_PREFIX + "checkGitLogIndexing"

fun <T : CommandChain> T.checkWarmupBuild(): T {
  addCommand(BUILD_PREFIX)
  return this
}

fun <T : CommandChain> T.checkGitLogIndexing(): T {
  addCommand(GIT_LOG_PREFIX)
  return this
}