// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WellKnownCommandTest {
  @Test
  fun `flavor-setup is headless and loads no non-bundled plugin`() {
    val command = WellKnownCommand.getCommandFor(listOf("flavor-setup"))
    assertThat(command).isNotNull
    assertThat(command!!.isHeadless).isTrue()
    assertThat(command.disablesNonBundledPlugins).isTrue()
  }

  @Test
  fun `other headless commands load the non-bundled plugins`() {
    for (name in listOf("installPlugins", "update", "inspect")) {
      assertThat(WellKnownCommand.getCommandFor(listOf(name))?.disablesNonBundledPlugins).describedAs(name).isFalse()
    }
  }
}
