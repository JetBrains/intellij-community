// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.terminal

import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider

internal class GitCommandSpecsProvider : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> = listOf(
    ShellCommandSpecInfo.create(gitOverrideSpec, ShellCommandSpecConflictStrategy.OVERRIDE)
  )
}
