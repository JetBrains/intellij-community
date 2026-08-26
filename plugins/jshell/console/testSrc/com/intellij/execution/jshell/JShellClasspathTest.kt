// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class JShellClasspathTest {
  @Test
  fun frontendIsOnTheClasspath() {
    assertThat(JShellClasspath.findFrontendJar())
      .describedAs("The jshell-frontend library must stay in the intellij.java.jshell.execution module")
      .isNotNull()
  }
}
