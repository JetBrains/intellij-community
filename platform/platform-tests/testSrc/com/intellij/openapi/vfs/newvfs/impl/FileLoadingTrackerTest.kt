// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestApplication
class FileLoadingTrackerTest {
  @Test
  fun `loading of a tracked file is logged with a stacktrace`(@TempDir tempDir: Path) {
    val dir = materialize(tempDir)
    val tracked = Files.createFile(tempDir.resolve("tracked.txt"))
    val untracked = Files.createFile(tempDir.resolve("untracked.txt"))

    val records = collectLoadingRecords {
      FileLoadingTracker.startTracking(listOf("${dir.path}/tracked.txt")).use {
        materialize(untracked)
        materialize(tracked)
      }
    }

    assertEquals(listOf("Loading ${dir.path}/tracked.txt"), records.map { it.message })
    assertNotNull(records.single().thrown) { "the logged loading must carry the caller stacktrace" }
  }

  private fun collectLoadingRecords(action: () -> Unit): List<LogRecord> {
    val records = CopyOnWriteArrayList<LogRecord>()
    val handler = object : Handler() {
      override fun publish(record: LogRecord) {
        if (record.message?.startsWith("Loading ") == true) {
          records.add(record)
        }
      }

      override fun flush() {}

      override fun close() {}
    }

    val logger = Logger.getLogger("#" + FileLoadingTracker::class.java.name)
    logger.addHandler(handler)
    try {
      action()
    }
    finally {
      logger.removeHandler(handler)
    }
    return records
  }

  private fun materialize(path: Path): VirtualFile {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    assertNotNull(file) { "file must exist in VFS: $path" }
    return file
  }
}
