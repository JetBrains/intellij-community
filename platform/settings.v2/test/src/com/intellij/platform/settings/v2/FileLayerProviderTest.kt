// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Checks the real file-based layer provider, [FileLayerService] with `JsonFileLayerProvider`, from end to end.
 *
 * A write of each supported value type at a directory context creates the local config file of that directory. A read
 * resolves through the context chain, so a deeper directory shadows an ancestor. A committed snapshot holds the
 * expected JSON. Run with `-Didea.tests.overwrite.data=true` to overwrite the snapshot.
 *
 * A save to a directory that no longer exists removes the value from the model, notifies the listeners and logs a
 * warning.
 */
@TestApplication
class FileLayerProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val str = SettingsKey("test.fp.str", default = "")
    private val num = SettingsKey("test.fp.num", default = 0)
    private val flag = SettingsKey("test.fp.flag", default = false)
    private val tags = SettingsKey("test.fp.tags", default = emptyList(), serializer = StringSerializer.nullable().listOf())
    private val limits = SettingsKey("test.fp.limits", default = emptyMap(), serializer = IntSerializer.nullable().mapOf())
    private val opt = SettingsKey<String?>("test.fp.opt")

    private const val SNAPSHOT_DIR = "fileLayerProvider"
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `values written per directory land in a json file, resolve through the context chain, and match the snapshot`(): Unit = runBlocking {
    val deepDir = VfsTestUtil.createFile(sourceRoot, "a/b/c/anchor.txt").parent
    val upperDir = readAction { sourceRoot.findChild("a")!! }
    val deepCtx = readAction { SettingsContext.file(deepDir, project) }
    val upperCtx = readAction { SettingsContext.file(upperDir, project) }

    // The two depths hold different values of the same keys. `opt` and some collection elements are null, so a null
    // value and a null element both make a round trip. The null `opt` of the deep directory shadows the ancestor value.
    writeAll(deepCtx, "deep", 1, true, listOf("x", null, "y"), mapOf("a" to 1, "n" to null), null)
    writeAll(upperCtx, "up", 2, false, listOf("z"), mapOf("b" to 2), "up-opt")

    assertValues(deepCtx, "deep", 1, true, listOf("x", null, "y"), mapOf("a" to 1, "n" to null), null)
    assertValues(upperCtx, "up", 2, false, listOf("z"), mapOf("b" to 2), "up-opt")

    assertMatchesSnapshot(deepDir, "deep.jetbrains.json")
    assertMatchesSnapshot(upperDir, "upper.jetbrains.json")
  }

  @Test
  fun `a save to a directory that no longer exists is dropped and leaves the model`(): Unit = runBlocking {
    val goneDir = VfsTestUtil.createFile(sourceRoot, "gone/anchor.txt").parent
    val goneCtx = readAction { SettingsContext.file(goneDir, project) }
    val gonePath = goneDir.toNioPath()
    VfsTestUtil.deleteFile(goneDir)

    str[goneCtx] = "lost"
    assertEquals("lost", str[goneCtx])  // read-your-writes, before the debounced save

    awaitEquals("") { str[goneCtx] }  // the dropped save removed the value from the model
    assertFalse(Files.exists(gonePath))  // and the save did not create the directory again
  }

  @Test
  fun `a dropped save notifies the listeners of the removed value`(): Unit = runBlocking {
    val goneDir = VfsTestUtil.createFile(sourceRoot, "gone-listened/anchor.txt").parent
    val goneCtx = readAction { SettingsContext.file(goneDir, project) }
    VfsTestUtil.deleteFile(goneDir)
    val seen = CopyOnWriteArrayList<String>()
    str.addListener(disposable, goneCtx) { _, context -> seen.add(str[context]) }

    str[goneCtx] = "lost"

    awaitEquals(listOf("lost", "")) { seen.toList() }  // the write, then the removal by the dropped save
  }

  @Test
  fun `a dropped save logs a warning with the dropped settings as json`(): Unit = runBlocking {
    val goneDir = VfsTestUtil.createFile(sourceRoot, "gone-logged/anchor.txt").parent
    val goneCtx = readAction { SettingsContext.file(goneDir, project) }
    VfsTestUtil.deleteFile(goneDir)
    val warnings = CopyOnWriteArrayList<String>()
    val warningCollector = object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        if (goneCtx.identity in message) {
          warnings.add(message)
          return false
        }
        return super.processWarn(category, message, t)
      }
    }

    LoggedErrorProcessor.executeWith(warningCollector).use {
      str[goneCtx] = "lost"
      awaitEquals(1) { warnings.size }
    }

    assertTrue("\"${str.identity}\": \"lost\"" in warnings.single(), warnings.single())
  }

  private suspend fun assertMatchesSnapshot(dir: VirtualFile, snapshot: String) =
    assertConfigMatchesSnapshot(dir, "$SNAPSHOT_DIR/$snapshot")

  private suspend fun writeAll(context: SettingsContext, s: String, n: Int, b: Boolean, t: List<String?>, m: Map<String, Int?>, o: String?) {
    str[context] = s
    num[context] = n
    flag[context] = b
    tags[context] = t
    limits[context] = m
    opt[context] = o
  }

  /** Asserts the value of each key at [context]. A read sees a completed write at once, before the debounced save. */
  private fun assertValues(context: SettingsContext, s: String, n: Int, b: Boolean, t: List<String?>, m: Map<String, Int?>, o: String?) {
    assertEquals(s, str[context])
    assertEquals(n, num[context])
    assertEquals(b, flag[context])
    assertEquals(t, tags[context])
    assertEquals(m, limits[context])
    assertEquals(o, opt[context])  // at the deep context, a null shows that the stored null shadows the ancestor value
  }

}
