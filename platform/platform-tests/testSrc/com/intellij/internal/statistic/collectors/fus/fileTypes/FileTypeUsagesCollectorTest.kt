// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.internal.statistic.beans.MetricEvent
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
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

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

  private fun refresh(root: Path) {
    val dir = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)) { "No VFS file for $root" }
    dir.refresh(false, true)
  }

  private fun countsBy(events: Set<MetricEvent>, eventId: String, key: String): Map<Any?, Any?> =
    events.filter { it.eventId == eventId }.associate {
      val data = it.data.build()
      data[key] to data["count"]
    }
}
