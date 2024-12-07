// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArgument
import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArguments
import org.junit.jupiter.api.Assertions

abstract class GradleJvmArgumentsParserTestCase {

  fun GradleJvmArguments.assertNoTokens() = apply {
    Assertions.assertEquals(emptyList<String>(), tokens)
  }

  fun GradleJvmArguments.assertTokens(vararg expectedArguments: String) = apply {
    Assertions.assertEquals(expectedArguments.toList(), tokens)
  }

  fun GradleJvmArguments.assertNoArguments() = apply {
    Assertions.assertEquals(emptyList<GradleJvmArgument>(), arguments)
  }

  fun GradleJvmArguments.assertArguments(vararg expectedArguments: GradleJvmArgument) = apply {
    Assertions.assertEquals(expectedArguments.toList(), arguments)
  }

  fun GradleJvmArguments.assertText(text: String) = apply {
    Assertions.assertEquals(text, text)
  }
}