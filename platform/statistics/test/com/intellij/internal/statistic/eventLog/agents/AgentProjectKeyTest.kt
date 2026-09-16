// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.agents

import com.intellij.internal.statistic.eventLog.agents.AgentProjectKey.isProjectBaseDirectory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The decision that gates the `project` field on a turn event. A wrong match would attribute a session to a project
 * the user never opened for it, and a wrong miss would drop the field and report nothing with no error to show why.
 *
 * The test drives the path decision, not `ProjectManager`. Opening a real project only to read its base directory
 * adds no coverage of the logic, because every mistake this guards is a path mistake.
 */
class AgentProjectKeyTest {
  @Test
  fun `the base directory matches itself`(@TempDir dir: Path) {
    assertTrue(isProjectBaseDirectory(dir.toString(), dir))
  }

  @Test
  fun `a subdirectory does not match`(@TempDir dir: Path) {
    val child = Files.createDirectory(dir.resolve("src"))

    assertFalse(isProjectBaseDirectory(dir.toString(), child))
  }

  @Test
  fun `a parent directory does not match`(@TempDir dir: Path) {
    val child = Files.createDirectory(dir.resolve("module"))

    assertFalse(isProjectBaseDirectory(child.toString(), dir))
  }

  @Test
  fun `an unrelated directory does not match`(@TempDir dir: Path) {
    val other = Files.createDirectory(dir.resolve("other"))
    val project = Files.createDirectory(dir.resolve("project"))

    assertFalse(isProjectBaseDirectory(project.toString(), other))
  }

  @Test
  fun `a project with no base directory never matches`(@TempDir dir: Path) {
    assertFalse(isProjectBaseDirectory(null, dir))
  }

  @Test
  fun `an unnormalized working directory matches`(@TempDir dir: Path) {
    val child = Files.createDirectory(dir.resolve("src"))
    val roundabout = child.resolve("..")

    assertTrue(isProjectBaseDirectory(dir.toString(), roundabout))
  }

  @Test
  fun `a symlinked working directory matches the resolved base directory`(@TempDir dir: Path) {
    val real = Files.createDirectory(dir.resolve("real"))
    val link = Files.createSymbolicLink(dir.resolve("link"), real)

    // The base directory is stored resolved and the agent reports the link, which is what a deeplink or a
    // terminal working directory can hold.
    assertTrue(isProjectBaseDirectory(real.toString(), link))
  }

  @Test
  fun `a symlinked base directory matches the resolved working directory`(@TempDir dir: Path) {
    val real = Files.createDirectory(dir.resolve("real"))
    val link = Files.createSymbolicLink(dir.resolve("link"), real)

    assertTrue(isProjectBaseDirectory(link.toString(), real))
  }

  @Test
  fun `a working directory that does not exist does not match`(@TempDir dir: Path) {
    val missing = dir.resolve("gone")

    assertFalse(isProjectBaseDirectory(dir.resolve("real").toString(), missing))
  }
}
