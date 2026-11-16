// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.openapi.application.productMigration.MigrationRedirectStart
import com.intellij.openapi.application.productMigration.migrationRedirectStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class MigrationRedirectStartTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private lateinit var currentDir: Path
  private lateinit var file: Path
  private lateinit var project: Path

  @Before
  fun createFiles() {
    currentDir = tempDir.root.toPath()
    file = currentDir.resolve("main.go").createFile()
    project = currentDir.resolve("project").createDirectories()
  }

  @Test
  fun `start with no positional argument is an open start`() {
    assertEquals(MigrationRedirectStart.OPEN, start())
    assertEquals(MigrationRedirectStart.OPEN, start("nosplash"))
    assertEquals(MigrationRedirectStart.OPEN, start("-Didea.config.path=/x"))
  }

  @Test
  fun `start with files and projects is redirected`() {
    assertEquals(MigrationRedirectStart.OPEN, start(file.toString()))
    assertEquals(MigrationRedirectStart.OPEN, start(project.toString()))
    assertEquals(MigrationRedirectStart.OPEN, start("main.go"))
  }

  @Test
  fun `start with an option is redirected`() {
    assertEquals(MigrationRedirectStart.OPEN, start("-l", "10", file.toString()))
    assertEquals(MigrationRedirectStart.OPEN, start("--line", "10", file.toString()))
    assertEquals(MigrationRedirectStart.OPEN, start("-e", "new.go"))
  }

  @Test
  fun `wait option in any position gives a start that waits`() {
    assertEquals(MigrationRedirectStart.OPEN_AND_WAIT, start("--wait"))
    assertEquals(MigrationRedirectStart.OPEN_AND_WAIT, start("--wait", file.toString()))
    assertEquals(MigrationRedirectStart.OPEN_AND_WAIT, start(file.toString(), "--wait"))
    assertEquals(MigrationRedirectStart.OPEN_AND_WAIT, start("-l", "10", file.toString(), "--wait"))
  }

  @Test
  fun `start with a URL is redirected`() {
    assertEquals(MigrationRedirectStart.OPEN, start("jetbrains://idea/navigate/reference?project=a&path=b"))
  }

  @Test
  fun `diff and merge are redirected as commands`() {
    assertEquals(MigrationRedirectStart.GUI_COMMAND, start("diff", "a", "b"))
    assertEquals(MigrationRedirectStart.GUI_COMMAND, start("merge", "a", "b", "c", "d"))
  }

  @Test
  fun `headless commands are not redirected`() {
    assertNull(start("format", "-h"))
    assertNull(start("inspect"))
    assertNull(start("installPlugins", "a"))
    assertNull(start("myinspect"))
  }

  @Test
  fun `client modes and remote dev commands are not redirected`() {
    assertNull(start("thinClient"))
    assertNull(start("ijLight"))
    assertNull(start("serverMode"))
  }

  @Test
  fun `unknown first argument that is not a path is not redirected`() {
    assertNull(start("myPluginCommand", "a"))
  }

  @Test
  fun `property of this IDE before a file is ignored`() {
    assertEquals(MigrationRedirectStart.OPEN, start("-Didea.config.path=/x", file.toString()))
    assertNull(start("-Didea.config.path=/x", "format"))
  }

  private fun start(vararg args: String): MigrationRedirectStart? = migrationRedirectStart(args.toList(), currentDir)
}
