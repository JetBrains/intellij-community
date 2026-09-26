// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.node

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface GradleCommandLineOption : GradleCommandLineNode {

  val name: String

  val values: List<String>

  data class ShortNotation(override val name: String, val value: String) : GradleCommandLineOption {

    override val tokens: List<String> = listOf("$name$value")

    override val values: List<String> = listOf(value)
  }

  data class LongNotation(override val name: String, val value: String) : GradleCommandLineOption {

    override val tokens: List<String> = listOf("$name=$value")

    override val values: List<String> = listOf(value)
  }

  data class PropertyNotation(override val name: String, val propertyName: String, val propertyValue: String) : GradleCommandLineOption {

    override val tokens: List<String> = listOf("$name$propertyName=$propertyValue")

    override val values: List<String> = listOf("$propertyName=$propertyValue")
  }

  data class VarargNotation(override val name: String, override val values: List<String>) : GradleCommandLineOption {

    override val tokens: List<String> = listOf(name) + values
  }
}