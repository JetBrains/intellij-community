// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.jvmArgs

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineNode
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption

@ApiStatus.Experimental
sealed interface GradleJvmArgument : GradleCommandLineNode {

  data class ValueNotation(val value: String) : GradleJvmArgument {

    override val text: String = value

    override val tokens: List<String> = listOf(value)
  }

  data class OptionNotation(val option: GradleCommandLineOption) : GradleJvmArgument {

    override val text: String = option.text

    override val tokens: List<String> = option.tokens
  }
}