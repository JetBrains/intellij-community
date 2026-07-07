// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.SettingsVaultImpl
import com.intellij.platform.settings.v2.impl.cache.CachedLayer
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheSerializer
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheStorage
import com.intellij.platform.settings.v2.impl.cache.SettingsSnapshotCache
import com.intellij.platform.settings.v2.impl.providers.JsonFileLayerProvider
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Pins the snapshot cache against the live vault. A dump that [SettingsSnapshotCache.saveForTest] writes decodes and
 * holds a value written just before. [SettingsSnapshotCache.load] installs a cache file from disk into the vault, so
 * the value resolves. The dump has a stable order that the content decides. Each test uses the context of a fresh
 * directory, which nothing else filled, so the load does not skip the cached layer as one already present. The
 * automatic load and save are off in unit tests. See [com.intellij.platform.settings.v2.impl.cache.isSnapshotCacheEnabled].
 */
@TestApplication
class SettingsSnapshotCacheTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.snap.style", default = "def")
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile
  private val vault get() = SettingsVault.getInstance() as SettingsVaultImpl

  @Test
  fun `a saved cache is decodable and holds a just-written value`(@TempDir tempDir: Path): Unit = runBlocking {
    val dir = directory("snap-save")
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    key[dirCtx] = "written"  // into the file-provider local layer at the directory (model updated synchronously)

    val file = tempDir.resolve("project.bin")
    SettingsSnapshotCache(file, project = project).saveForTest()

    val layers = SettingsCacheSerializer.decode(Files.readAllBytes(file))!!
    assertTrue(layers.any { it.value.values[key.identity] == StringValue("written") },
               "the dumped cache must hold the written value")
  }

  @Test
  fun `load seeds a value from the cache file into the vault`(@TempDir tempDir: Path): Unit = runBlocking {
    val dir = directory("snap-load")
    val dirCtx = readAction { SettingsContext.file(dir, project) }

    // Hand-build a cache file as a prior session would have left it: the key set in this directory's file layer.
    val layer = CachedLayer(
      contextId = dirCtx.identity,
      providerId = JsonFileLayerProvider::class.java.name,
      locality = "LOCAL",
      value = MapValue(mapOf(key.identity to StringValue("cached"))),
    )
    val file = tempDir.resolve("project.bin")
    SettingsCacheStorage.write(file, SettingsCacheSerializer.encode(listOf(layer)))

    SettingsSnapshotCache(file, project = project).load()

    awaitEquals("cached") { key[dirCtx] }  // the seeded layer resolves
  }

  @Test
  fun `dump order is content-determined, not insertion order`(): Unit = runBlocking {
    // Write to the later-sorting directory first, so the model's insertion order differs from the sorted order.
    val dirZ = directory("z-order")
    val dirA = directory("a-order")
    key[readAction { SettingsContext.file(dirZ, project) }] = "z"
    key[readAction { SettingsContext.file(dirA, project) }] = "a"

    val dump = vault.dump(project)
    val ids = dump.map { it.contextId }
    assertEquals(ids.sorted(), ids, "dump must be in a content-determined (sorted) order, not the model's insertion order")
    assertEquals(dump, vault.dump(project), "dump must be stable across calls, so the unchanged-skip holds")
  }

  /**
   * Creates a fresh directory under the source root through an anchor file, so the cache seeds a context that
   * nothing else touched.
   */
  private fun directory(name: String): VirtualFile = VfsTestUtil.createFile(sourceRoot, "$name/anchor.txt").parent
}
