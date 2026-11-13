// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.project

import com.intellij.openapi.roots.impl.countReadActions
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class ProjectSizeTest {
  @Nested
  inner class Sum {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    @Test
    fun `sums the content files and skips the project files`(): Unit = timeoutRunBlocking {
      val root = tempDirFixture.get()
      withContext(Dispatchers.IO) {
        root.resolve("a.txt").writeText("12345")
        root.resolve("sub").createDirectories().resolve("b.txt").writeText("1234567")
        root.resolve(".idea").createDirectories().resolve("misc.xml").writeText("<project version=\"4\"/>")
        refresh(root)
      }
      IndexingTestUtil.suspendUntilIndexesAreReady(project)

      val size = computeProjectSize(project)

      assertEquals(12L, size.sizeBytes)
      assertEquals(false, size.limitReached)
    }
  }

  @Nested
  inner class ReadActions {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /** The walk takes no read action per file. The only read actions are the ones of the roots step. */
    @Test
    fun `takes no read action per file`(): Unit = timeoutRunBlocking(timeout = 10.minutes) {
      val root = tempDirFixture.get()
      withContext(Dispatchers.IO) {
        repeat(DIRECTORIES) { dir ->
          val directory = root.resolve("d$dir").createDirectories()
          repeat(FILES_PER_DIRECTORY) { file -> directory.resolve("f$file.txt").writeText("a") }
        }
        refresh(root)
      }
      IndexingTestUtil.suspendUntilIndexesAreReady(project)

      val (size, readActions) = countReadActions { computeProjectSize(project) }

      assertEquals((DIRECTORIES * FILES_PER_DIRECTORY).toLong(), size.sizeBytes)
      assertTrue(
        readActions <= MAX_READ_ACTIONS,
        "${DIRECTORIES * FILES_PER_DIRECTORY} files took $readActions read actions, " +
        "and the limit is $MAX_READ_ACTIONS",
      )
    }
  }

  private fun refresh(root: Path) {
    val dir = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)) { "No VFS file for $root" }
    dir.refresh(false, true)
  }
}

private const val DIRECTORIES = 3
private const val FILES_PER_DIRECTORY = 10

/** The read actions of the roots step, with a margin. A walk with one read action per file takes more than 30. */
private const val MAX_READ_ACTIONS = 3
