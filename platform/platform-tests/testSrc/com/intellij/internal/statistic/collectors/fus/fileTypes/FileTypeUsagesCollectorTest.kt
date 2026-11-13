// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class FileTypeUsagesCollectorTest {
  private val tempDirFixture = tempPathFixture()
  private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
  private val project by projectFixture

  @Suppress("unused")
  private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

  @Test
  fun `counts indexed content files by type and extension and skips the project store`(): Unit = timeoutRunBlocking {
    val root = tempDirFixture.get()
    withContext(Dispatchers.IO) {
      root.resolve("a.txt").writeText("a")
      root.resolve("b.txt").writeText("b")
      root.resolve("c.xml").writeText("<c/>")
      // empty, so its type does not depend on content detection
      root.resolve("noext").createFile()
      root.resolve(".idea").createDirectories().resolve("misc.xml").writeText("<project version=\"4\"/>")
      refresh(root)
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val events = FileTypeUsagesCollector().collect(project)

    assertEquals(
      mapOf("PLAIN_TEXT" to 2, "XML" to 1, "AUTO_DETECTED" to 1),
      countsBy(events, "file.type.in.project", "file_type"),
    )
    assertEquals(
      mapOf("txt" to 2, "xml" to 1),
      countsBy(events, "file.extension.in.project", "file_extension"),
    )
  }

  /**
   * The collector reads the index in batches, so the read action count follows the batch count, not the file count.
   * Before the change the collector took one read action per registered file type, and the walk before it took two
   * read actions per file.
   */
  @Test
  fun `takes a read action per batch of files, not per file`(): Unit = timeoutRunBlocking(timeout = 10.minutes) {
    val root = tempDirFixture.get()
    withContext(Dispatchers.IO) {
      val textDir = root.resolve("text").createDirectories()
      val xmlDir = root.resolve("xml").createDirectories()
      repeat(FILES_OF_EACH_TYPE) { index -> textDir.resolve("f$index.txt").writeText("a") }
      repeat(FILES_OF_EACH_TYPE) { index -> xmlDir.resolve("f$index.xml").writeText("<a/>") }
      refresh(root)
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val counter = ReadActionCounter()
    val counterDisposable = Disposer.newDisposable()
    val events = try {
      ApplicationManagerEx.getApplicationEx().addReadActionListener(counter, counterDisposable)
      FileTypeUsagesCollector().collect(project)
    }
    finally {
      Disposer.dispose(counterDisposable)
    }

    assertTrue(events.isNotEmpty(), "The collector reported nothing")
    assertTrue(
      counter.count <= MAX_READ_ACTIONS,
      "${2 * FILES_OF_EACH_TYPE} files took ${counter.count} read actions, and the limit is $MAX_READ_ACTIONS",
    )
  }

  private fun refresh(root: Path) {
    val dir = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)) { "No VFS file for $root" }
    dir.refresh(false, true)
  }

  private fun countsBy(events: Set<MetricEvent>, eventId: String, key: String): Map<Any?, Any?> =
    events.filter { it.eventId == eventId }.associate {
      val data = it.data.build()
      data[key] to data["count"]
    }

  /** Counts the outermost read actions of each thread. */
  private class ReadActionCounter : ReadActionListener {
    private val depth = ThreadLocal.withInitial { 0 }
    private val counter = AtomicInteger()

    val count: Int get() = counter.get()

    override fun readActionStarted(action: Class<*>) {
      depth.set(depth.get() + 1)
    }

    override fun readActionFinished(action: Class<*>) {
      val left = depth.get() - 1
      depth.set(left)
      if (left == 0) {
        counter.incrementAndGet()
      }
    }
  }
}

private const val FILES_OF_EACH_TYPE = 1250

/** One read action for the keys, and for each of the few indexed types one for the iterator and one per batch. */
private const val MAX_READ_ACTIONS = 25
