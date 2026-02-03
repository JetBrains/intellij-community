// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.node

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class GradleCommandLineTask(val name: String, val options: GradleCommandLineOptions) : GradleCommandLineNode {

  override val tokens: List<String> = listOf(name) + options.tokens

  constructor(name: String, vararg options: GradleCommandLineOption)
    : this(name, options.toList())

  constructor(name: String, options: List<GradleCommandLineOption>)
    : this(name, GradleCommandLineOptions(options))
}