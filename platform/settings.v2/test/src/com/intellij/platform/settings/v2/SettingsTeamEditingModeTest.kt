// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the [EDIT_TEAM_LAYER] switch through the real file layer provider. A plain [KeyLocality.SHAREABLE] write to a
 * directory context lands in the local config by default, and in the team config with the switch on. The tests set
 * the switch at the **project** context, because the key takes only the application and the project. A directory
 * write finds it up its context chain. The file provider creates a config only on its first write (ADR 0152). So the
 * one file that exists shows where the write landed, and no file is created before to hide it.
 *
 * Each test sets the switch explicitly, so the two are order-independent within this class's shared project.
 */
@TestApplication
class SettingsTeamEditingModeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val shareKey = SettingsKey("test.tem.share", default = "def")
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `with the switch off, a shareable write lands in the local config`(): Unit = runBlocking {
    val dir = directory("tem-off")
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    EDIT_TEAM_LAYER[SettingsContext.project(project)] = false

    shareKey[dirCtx] = "written"
    assertEquals("written", shareKey[dirCtx])

    awaitConfigContains(dir, LayerLocality.LOCAL, "written")  // the first write created the local config
    assertNull(readConfigOrNull(dir, LayerLocality.TEAM))     // and no team config was created
  }

  @Test
  fun `with the switch on, a shareable write lands in the team config`(): Unit = runBlocking {
    val dir = directory("tem-on")
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    EDIT_TEAM_LAYER[SettingsContext.project(project)] = true

    shareKey[dirCtx] = "written"
    assertEquals("written", shareKey[dirCtx])

    awaitConfigContains(dir, LayerLocality.TEAM, "written")   // the first write created the team config instead
    assertNull(readConfigOrNull(dir, LayerLocality.LOCAL))    // and no local config was created
  }

  /** Creates a fresh directory under the source root through an anchor file, so each test owns its config. */
  private fun directory(name: String): VirtualFile = VfsTestUtil.createFile(sourceRoot, "$name/anchor.txt").parent
}
