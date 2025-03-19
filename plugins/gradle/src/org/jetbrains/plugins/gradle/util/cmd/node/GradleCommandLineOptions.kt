// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.node

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class GradleCommandLineOptions(
  val options: List<GradleCommandLineOption>
) : GradleCommandLineNode,
    List<GradleCommandLineOption> by options {

  override val tokens: List<String> = options.flatMap { it.tokens }
}