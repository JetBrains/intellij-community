// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the per-file layers. The config file of a directory holds the keys of the directory and a synthetic
 * `@@perfile` map. The map goes from a file name to the overrides of that file. [FileLayerService] splits the map into
 * one layer per file context. So a write at a file context overrides the directory value for that file only. A sibling
 * file still inherits the directory value. A delete of the config file clears the directory values and the overrides.
 *
 * The class also checks the change listeners and the slotted keys at a file context.
 */
@TestApplication
class PerFileLayerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val style = SettingsKey("test.perfile.style", default = "def")

    // The slotted keys of the multidimensional cases: a style per language, selected by the current language.
    private val lang = SettingsKey("test.perfile.lang", default = "kotlin")
    private val langData = SettingsMapKey("test.perfile.langData", discriminatorKey = lang)
    private val langStyle = SettingsKey("test.perfile.langStyle", default = "none", storageKey = langData)

    private const val SNAPSHOT_DIR = "perFileLayer"
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `a file-context write overrides the directory only for that file, siblings inherit`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "perfile1/foo.txt").parent
    val foo = readAction { dir.findChild("foo.txt")!! }
    val bar = VfsTestUtil.createFile(dir, "bar.txt")
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    val fooCtx = readAction { SettingsContext.file(foo, project) }
    val barCtx = readAction { SettingsContext.file(bar, project) }

    style[dirCtx] = "dir"
    style[fooCtx] = "foo"

    assertEquals("foo", style[fooCtx])   // per-file override wins for foo
    assertEquals("dir", style[barCtx])   // sibling with no per-file entry inherits the directory value
    assertEquals("dir", style[dirCtx])   // the directory level itself is unchanged

    // The generated on-disk config, as a committed artifact (overwrite with -Didea.tests.overwrite.data=true).
    assertMatchesSnapshot(dir, "dirWithPerFile.jetbrains.json")
  }

  @Test
  fun `deleting the config clears both the directory and the per-file overrides`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "perfile2/foo.txt").parent
    val foo = readAction { dir.findChild("foo.txt")!! }
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    val fooCtx = readAction { SettingsContext.file(foo, project) }

    style[dirCtx] = "dir"
    style[fooCtx] = "foo"
    assertEquals("foo", style[fooCtx])
    assertEquals("dir", style[dirCtx])
    awaitConfigFile(dir)  // read-your-writes makes the reads above immediate; wait for the debounced persist before deleting

    // The writes above land in the local config (default locality); delete that file to clear them.
    val config = dir.toNioPath().resolve(localConfigName(FileLayerService.CONFIG_FILE))
    VfsTestUtil.deleteFile(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(config)!!)

    awaitEquals("def") { style[fooCtx] }   // per-file override gone → key default
    awaitEquals("def") { style[dirCtx] }   // directory value gone → key default
  }

  /**
   * Checks only the coexistence and the edit, not a delete. A deleted file keeps its per-file entry until the next load
   * of the config, because the vault still holds its slice. Only a change of a config file loads it again.
   */
  @Test
  fun `two per-file entries coexist and editing one preserves the other`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "perfile3/foo.txt").parent
    val foo = readAction { dir.findChild("foo.txt")!! }
    val bar = VfsTestUtil.createFile(dir, "bar.txt")
    val fooCtx = readAction { SettingsContext.file(foo, project) }
    val barCtx = readAction { SettingsContext.file(bar, project) }

    style[fooCtx] = "foo"
    style[barCtx] = "bar"
    assertEquals("foo", style[fooCtx])
    assertEquals("bar", style[barCtx])
    assertMatchesSnapshot(dir, "twoFiles.jetbrains.json")

    // Editing one file's override rewrites the whole directory config and preserves the other file's entry.
    style[barCtx] = "bar2"
    assertEquals("bar2", style[barCtx])
    assertEquals("foo", style[fooCtx])
    assertMatchesSnapshot(dir, "afterDropAndEdit.jetbrains.json")
  }

  @Test
  fun `a save removes the per-file entries that the load ignores`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "perfileU1/sub/anchor.txt").parent.parent
    val foo = VfsTestUtil.createFile(dir, "foo.txt")
    val bar = VfsTestUtil.createFile(dir, "bar.txt")
    val fooCtx = readAction { SettingsContext.file(foo, project) }
    val barCtx = readAction { SettingsContext.file(bar, project) }
    seedLocalConfig(dir, "ignoredEntries.input.jetbrains.json")
    assertEquals("foo", style[fooCtx])

    style[barCtx] = "bar"

    assertMatchesSnapshot(dir, "ignoredEntriesRemoved.jetbrains.json")
  }

  @Test
  fun `a per-file change notifies a file listener but not a directory listener`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "perfileL/foo.txt").parent
    val foo = readAction { dir.findChild("foo.txt")!! }
    val bar = VfsTestUtil.createFile(dir, "bar.txt")
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    val fooCtx = readAction { SettingsContext.file(foo, project) }
    val barCtx = readAction { SettingsContext.file(bar, project) }

    val fileFires = AtomicInteger(0)
    val dirFires = AtomicInteger(0)
    val fenceFires = AtomicInteger(0)
    style.addListener(disposable, fooCtx) { _, _ -> fileFires.incrementAndGet() }
    style.addListener(disposable, dirCtx) { _, _ -> dirFires.incrementAndGet() }
    style.addListener(disposable, barCtx) { _, _ -> fenceFires.incrementAndGet() }

    style[fooCtx] = "foo"
    assertEquals(1, fileFires.get())  // a synchronous write delivers its listeners before returning

    style[barCtx] = "bar"
    assertEquals(1, fenceFires.get())

    assertEquals(0, dirFires.get())  // a per-file write never changes the directory's effective value
  }

  @Test
  fun `multidim resolves with the discriminator in the directory and the slot value in the file`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "perfileM1/foo.txt").parent
    val foo = readAction { dir.findChild("foo.txt")!! }
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    val fooCtx = readAction { SettingsContext.file(foo, project) }

    lang[dirCtx] = "java"             // discriminator lives at the directory
    assertEquals("java", lang[fooCtx])  // set synchronously, so the slotted write below selects its slot from it
    langStyle[fooCtx.accessFor(langStyle)] = "file-java"   // written per-file, into the slot the dir's discriminator selects

    assertEquals("file-java", langStyle[fooCtx])  // the file's java slot, selected by the directory's discriminator
    assertEquals("none", langStyle[dirCtx])        // the directory has no slot value of its own → default
  }

  @Test
  fun `multidim resolves with the discriminator in the file and the slot value in the directory`(): Unit = runBlocking {
    val dir = VfsTestUtil.createFile(sourceRoot, "perfileM2/foo.txt").parent
    val foo = readAction { dir.findChild("foo.txt")!! }
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    val fooCtx = readAction { SettingsContext.file(foo, project) }

    lang[fooCtx] = "java"                                          // discriminator lives per-file
    langStyle[dirCtx.access { this[lang] = "java" }] = "dir-java"  // slot value written at the directory (pinned slot)

    assertEquals("dir-java", langStyle[fooCtx])  // the file's discriminator selects the java slot, value inherited from the dir
  }

  private suspend fun assertMatchesSnapshot(dir: VirtualFile, snapshot: String) =
    assertConfigMatchesSnapshot(dir, "$SNAPSHOT_DIR/$snapshot")

  /** Writes the [input] resource as the local config of [dir] and reloads [dir], so the vault holds the config. */
  private suspend fun seedLocalConfig(dir: VirtualFile, input: String) {
    VfsTestUtil.createFile(dir, localConfigName(FileLayerService.CONFIG_FILE), Files.readString(Path.of(testResourcePath("$SNAPSHOT_DIR/$input"))))
    SettingsVault.getInstance().reload(readAction { SettingsContext.file(dir, project) })
  }

  /** Waits until the local config file of [dir] exists on disk. */
  private suspend fun awaitConfigFile(dir: VirtualFile) {
    withTimeout(10.seconds) {
      while (readConfigOrNull(dir) == null) delay(10.milliseconds)
    }
  }
}
