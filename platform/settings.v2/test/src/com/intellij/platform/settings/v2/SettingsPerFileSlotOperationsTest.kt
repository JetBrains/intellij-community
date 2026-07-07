// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the slot operations at a **file** context. The slotted storage of a file lives in the per-file container of
 * the directory config ([FileLayerService.PER_FILE_CONTAINER], then the file name). A copy, a drop or a rename at a
 * file changes the vault layer of that file. On a save, the file provider puts the layer back under the per-file
 * container and keeps the container structure. Each test seeds an input config, runs the operation, and compares the
 * output config to a committed snapshot.
 */
@TestApplication
class SettingsPerFileSlotOperationsTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val current = SettingsKey("test.pfslot.current", default = "dev")
    private val data = SettingsMapKey("test.pfslot.data", discriminatorKey = current)

    private val style = SettingsKey("test.pfslot.style", default = "none", storageKey = data)

    private const val DIR = "slotOps"
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `copy slot nests under the per-file container`(): Unit = runBlocking {
    verify("perfile.copy.json") { fileContext -> data.copySlot(fileContext, "dev", "stage") }
  }

  @Test
  fun `drop slot nests under the per-file container`(): Unit = runBlocking {
    verify("perfile.drop.json") { fileContext -> data.dropSlot(fileContext, "prod") }
  }

  @Test
  fun `rename slot nests under the per-file container`(): Unit = runBlocking {
    verify("perfile.rename.json") { fileContext -> data.renameSlot(fileContext, "dev", "dev2") }
  }

  /**
   * Seeds `perfile.input.json` as the local config of a fresh directory, and waits until the vault loads it. Then runs
   * [op] at the context of a file in that directory, and waits until the config on disk matches [expected].
   *
   * @param expected the name of the snapshot file in [DIR]
   * @param op the slot operation, given the file context
   */
  private suspend fun verify(expected: String, op: suspend (SettingsContext) -> Unit) {
    // A dir per operation, so the three tests don't share one directory config (and its vault layer).
    val foo = VfsTestUtil.createFile(sourceRoot, "pfslot-${expected.removePrefix("perfile.").removeSuffix(".json")}/foo.txt")
    val dir = readAction { foo.parent }
    // Seed into the local config. A slot op rewrites every layer at its context that holds the slot, and each layer
    // changes only its own map, so the output lands in the seeded local config.
    val input = Files.readString(Path.of(testResourcePath("$DIR/perfile.input.json")))
    VfsTestUtil.createFile(dir, localConfigName(FileLayerService.CONFIG_FILE), input)
    val fooContext = readAction { SettingsContext.file(foo, project) }
    awaitEquals("dev-style") { style[fooContext.access { this[current] = "dev" }] }

    op(fooContext)

    val expectedText = Files.readString(Path.of(testResourcePath("$DIR/$expected"))).trim()
    withTimeout(10.seconds) {
      while ((readConfigOrNull(dir) ?: "").trim() != expectedText) delay(10.milliseconds)
    }
    UsefulTestCase.assertSameLinesWithFile(testResourcePath("$DIR/$expected"), readConfigOrNull(dir) ?: "")
  }
}
