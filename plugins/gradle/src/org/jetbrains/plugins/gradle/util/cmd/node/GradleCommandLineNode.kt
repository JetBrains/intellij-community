// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.node

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface GradleCommandLineNode {

  val tokens: List<String>

  val text: String
    get() = tokens.joinToString(" ")
}