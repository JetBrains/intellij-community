// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.isMigrationRedirectStart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
  fun `start with no positional argument is redirected`() {
    assertTrue(start())
    assertTrue(start("nosplash"))
  }

  @Test
  fun `start with files and projects is redirected`() {
    assertTrue(start(file.toString()))
    assertTrue(start(project.toString()))
    assertTrue(start("main.go"))
  }

  @Test
  fun `start with an option or a URL is redirected`() {
    assertTrue(start("-l", "10", file.toString()))
    assertTrue(start("--wait", file.toString()))
    assertTrue(start("jetbrains://idea/navigate/reference?project=a&path=b"))
  }

  @Test
  fun `known commands are not redirected`() {
    assertFalse(start("format", "-h"))
    assertFalse(start("inspect"))
    assertFalse(start("diff", "a", "b"))
  }

  @Test
  fun `client modes and remote dev commands are not redirected`() {
    assertFalse(start("thinClient"))
    assertFalse(start("ijLight"))
    assertFalse(start("serverMode"))
  }

  @Test
  fun `unknown first argument that is not a path is not redirected`() {
    assertFalse(start("myPluginCommand", "a"))
  }

  private fun start(vararg args: String): Boolean = isMigrationRedirectStart(args.toList(), currentDir)
}
