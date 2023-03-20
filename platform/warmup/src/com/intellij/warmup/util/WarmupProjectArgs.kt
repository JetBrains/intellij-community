// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.platform.util.ArgsParser

interface WarmupProjectArgs : OpenProjectArgs {
  val build: Boolean
  val rebuild: Boolean
}

class WarmupProjectArgsImpl(parser: ArgsParser) : WarmupProjectArgs, OpenProjectArgsImpl(parser) {

  override val build: Boolean by parser.arg("build", "Build opened project. Cannot be specified together with --rebuild").flag()
  override val rebuild: Boolean by parser.arg("rebuild", "Rebuild opened project. Cannot be specified together with --build").flag()

}