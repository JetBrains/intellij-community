// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.platform.util.ArgsParser
import org.jetbrains.annotations.ApiStatus

interface WarmupProjectArgs : OpenProjectArgs {
  val build: Boolean
  val rebuild: Boolean
  val indexGitLog: Boolean
}

@ApiStatus.Internal
class WarmupProjectArgsImpl(parser: ArgsParser) : WarmupProjectArgs, OpenProjectArgsImpl(parser) {

  override val build: Boolean by parser.arg("build", "Build opened project. Cannot be specified together with --rebuild").flag()
  override val rebuild: Boolean by parser.arg("rebuild", "Rebuild opened project. Cannot be specified together with --build").flag()
  override val indexGitLog: Boolean by parser.arg("index-git-log", "Index git log").optional().boolean { false }
}