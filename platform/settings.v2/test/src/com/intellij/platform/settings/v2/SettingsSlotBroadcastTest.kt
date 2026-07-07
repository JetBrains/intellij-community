// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins that a slot operation **broadcasts across every locality** at its context (ADR 0078), on the files of the real
 * file layer provider. Each test seeds the same slot into the three locality configs of one directory:
 * `.jetbrains.json` for the team, `.jetbrains.local.json` for the local and `.jetbrains.env.json` for the
 * environmental locality. One copy, drop or rename at the directory context then rewrites **all three** files, not
 * only the local one that a plain write takes.
 * [SettingsSlotBroadcastAcrossLayersTest] pins the broadcast across providers and the match of the context.
 */
@TestApplication
class SettingsSlotBroadcastTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val current = SettingsKey("test.bcast.current", default = "dev")
    private val data = SettingsMapKey("test.bcast.data", discriminatorKey = current)
    private val style = SettingsKey("test.bcast.style", default = "none", storageKey = data)
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `rename broadcasts to every locality file`(): Unit = runBlocking {
    val dir = seedSlotInEveryLocality("bcast-rename")

    data.renameSlot(readAction { SettingsContext.file(dir, project) }, "dev", "dev2")

    for (locality in LayerLocality.entries) {
      awaitConfigContains(dir, locality, "dev2")
      assertFalse(readConfigOrNull(dir, locality)!!.contains("\"dev\""), "the $locality config must not keep the old slot")
    }
  }

  @Test
  fun `drop broadcasts to every locality file`(): Unit = runBlocking {
    val dir = seedSlotInEveryLocality("bcast-drop")

    data.dropSlot(readAction { SettingsContext.file(dir, project) }, "dev")

    for (locality in LayerLocality.entries) awaitConfigLacks(dir, locality, "dev")
  }

  @Test
  fun `copy broadcasts to every locality file, keeping the source`(): Unit = runBlocking {
    val dir = seedSlotInEveryLocality("bcast-copy")

    data.copySlot(readAction { SettingsContext.file(dir, project) }, "dev", "dev2")

    for (locality in LayerLocality.entries) {
      awaitConfigContains(dir, locality, "dev2")
      assertTrue(readConfigOrNull(dir, locality)!!.contains("\"dev\""), "the $locality config must keep the source slot")
    }
  }

  /**
   * Writes the `dev` slot into the three locality configs of a fresh directory, and reloads it into the vault. The
   * child value differs per locality, so the layers are distinct. Before it returns, it **checks that every locality
   * config on disk holds the slot**. So a later check that the slot is gone proves that the operation removed it.
   *
   * @param name the name of the fresh directory
   * @return the directory that holds the seeded configs
   */
  private suspend fun seedSlotInEveryLocality(name: String): VirtualFile {
    val dir = VfsTestUtil.createFile(sourceRoot, "$name/anchor.txt").parent
    for (locality in LayerLocality.entries) {
      VfsTestUtil.createFile(
        dir, localityConfigName(FileLayerService.CONFIG_FILE, locality),
        """{"${data.identity}": {"dev": {"${style.identity}": "${locality.name}"}}}""",
      )
    }
    SettingsVault.getInstance().reload(readAction { SettingsContext.file(dir, project) })
    for (locality in LayerLocality.entries) awaitConfigContains(dir, locality, "dev")
    return dir
  }
}
