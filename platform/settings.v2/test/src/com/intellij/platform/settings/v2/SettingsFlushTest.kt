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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks that [SettingsVault.flush] writes the pending debounced writes to disk **before it returns**. The test uses
 * the real file layer provider. A write persists only after a debounce, so a read of the file right after a plain write
 * can still miss it. After [SettingsVault.flush] it must not.
 *
 * A flush drains by context, not by provider. So a flush at the project reaches a file config under the project.
 * `SettingsAppLifecycleListener` and `SettingsProjectListener` rely on this guarantee at shutdown and at project close.
 */
@TestApplication
class SettingsFlushTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val shareKey = SettingsKey("test.flush.share", default = "def")
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `flush persists a pending write before returning`(): Unit = runBlocking {
    val dir = directory("flush-immediate")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    shareKey[dirCtx] = "written"                      // in the model, but its disk write is still debounced
    SettingsVault.getInstance().flush(dirCtx)         // forces it now, suspends until written

    // No polling on purpose: the write is on disk the instant flush returns, or the guarantee is broken.
    assertTrue(readConfigOrNull(dir, LayerLocality.LOCAL)?.contains("\"written\"") == true,
               "flush must have forced the debounced write to disk before returning")
  }

  @Test
  fun `flush at the project scope drains a file config nested beneath it`(): Unit = runBlocking {
    val dir = directory("flush-scope")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    shareKey[dirCtx] = "written"
    SettingsVault.getInstance().flush(SettingsContext.project(project))  // the directory context is under the project

    assertTrue(readConfigOrNull(dir, LayerLocality.LOCAL)?.contains("\"written\"") == true,
               "a project-scope flush must drain a file config beneath the project")
  }

  /** Creates the fresh directory [name] under the source root through an anchor file, so each test owns its config. */
  private fun directory(name: String): VirtualFile = VfsTestUtil.createFile(sourceRoot, "$name/anchor.txt").parent
}
