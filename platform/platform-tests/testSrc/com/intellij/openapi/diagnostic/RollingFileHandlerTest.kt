// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.text.allOccurrencesOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord

class RollingFileHandlerTest {
  @Rule @JvmField val tempDir = TempDirectory()

  private val msgOnlyFormatter = object : Formatter() {
    override fun format(record: LogRecord): String = record.message
  }

  @Test fun testRollingHandler() {
    val logName = "RollingFileHandlerTest.log"
    val logFile = tempDir.newFile(logName).toPath()
    val handler = RollingFileHandler(logFile, 100, 10, false)
    handler.formatter = msgOnlyFormatter

    val message1 = "a".repeat(80)
    handler.publish(LogRecord(Level.INFO, message1))
    assertEquals(message1, Files.readString(logFile))
    val message2 = "b".repeat(80)
    handler.publish(LogRecord(Level.INFO, message2))

    val logPath1 = logFile.resolveSibling(logName.replace(".log", ".1.log"))
    assertEquals(message1 + message2, Files.readString(logPath1))
    val message3 = "c".repeat(80)
    handler.publish(LogRecord(Level.INFO, message3))
    assertEquals(message3, Files.readString(logFile))
    val message4 = "d".repeat(80)
    handler.publish(LogRecord(Level.INFO, message4))

    val logPath2 = logFile.resolveSibling(logName.replace(".log", ".2.log"))
    assertEquals(message3 + message4, Files.readString(logPath1))
    assertEquals(message1 + message2, Files.readString(logPath2))
  }

  @Test fun noWriteAmplificationOnFailedRotate() {
    IoTestUtil.assumeUnix()
    val logDir = tempDir.newDirectoryPath()
    try {
      val logName = "RollingFileHandlerTest.log"
      val logFile = logDir.resolve(logName)
      val handler = RollingFileHandler(logFile, 100, 2, false)
      handler.formatter = msgOnlyFormatter
      NioFiles.setReadOnly(logDir, true)
      handler.publish(LogRecord(Level.INFO, "a".repeat(100)))
      handler.publish(LogRecord(Level.INFO, "b".repeat(100)))
      handler.publish(LogRecord(Level.INFO, "c".repeat(100)))
      handler.publish(LogRecord(Level.INFO, "d".repeat(100)))
      assertTrue(Files.exists(logFile))
      assertFalse(Files.exists(logFile.resolveSibling(logName.replace(".log", ".1.log"))))
      handler.flush()
      val content = Files.readString(logFile)
      assertEquals(1, content.allOccurrencesOf("Log rotate failed: ").count())
    }
    finally {
      NioFiles.setReadOnly(logDir, false)
    }
  }
}
