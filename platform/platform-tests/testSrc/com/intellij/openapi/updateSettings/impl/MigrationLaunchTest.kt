// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class MigrationLaunchTest {
  @Test
  fun `launch arguments have the version, the reason, the source product and the projects`() {
    val arguments = migrationLaunchArguments("GO", listOf(Path.of("/Users/me/my project"), Path.of("/Users/me/other")))
    assertEquals(listOf(
      "-Dide.migration.version=1",
      "-Dide.migration.reason=migrate",
      "-Dide.migration.source.product=GO",
      "/Users/me/my project",
      "/Users/me/other",
    ), arguments)
  }

  @Test
  fun `launch arguments without projects`() {
    assertEquals(listOf("-Dide.migration.version=1", "-Dide.migration.reason=migrate", "-Dide.migration.source.product=RR"),
                 migrationLaunchArguments("RR", emptyList()))
  }

  @Test
  fun `open command starts a new instance with separate arguments`() {
    val command = macOpenCommand(Path.of("/Users/me/Applications/Unified IDE.app"), listOf("-Da=b", "/Users/me/my project"))
    assertEquals(listOf("/usr/bin/open", "-n", "-a", "/Users/me/Applications/Unified IDE.app", "--args", "-Da=b", "/Users/me/my project"),
                 command)
  }
}
