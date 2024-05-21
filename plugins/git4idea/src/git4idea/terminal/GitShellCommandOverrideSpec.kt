// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package git4idea.terminal

import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec

internal val gitOverrideSpec = ShellCommandSpec("git") {
  addGitAliases()

  subcommands {
    // ...
  }
}
