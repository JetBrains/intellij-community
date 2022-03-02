// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.testFramework.TemporaryDirectory
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord

class RollingFileHandlerTest {
  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  @Test
  fun testRollingHandler() {
    val logPath = tempDir.newPath("RollingFileHandlerTest.log")
    val handler = RollingFileHandler(logPath, 100, 2, false)
    handler.formatter = object : Formatter() {
      override fun format(record: LogRecord): String {
        return record.message
      }
    }
    val message1 = "a".repeat(80)
    handler.publish(LogRecord(Level.INFO, message1))
    Assert.assertEquals(message1, Files.readString(logPath))
    val message2 = "b".repeat(80)
    handler.publish(LogRecord(Level.INFO, message2))

    val logPath1 = Paths.get(logPath.toString().replace(".log", ".1.log"))
    Assert.assertEquals(message1 + message2, Files.readString(logPath1))
    val message3 = "c".repeat(80)
    handler.publish(LogRecord(Level.INFO, message3))
    Assert.assertEquals(message3, Files.readString(logPath))
    val message4 = "d".repeat(80)
    handler.publish(LogRecord(Level.INFO, message4))

    val logPath2 = Paths.get(logPath.toString().replace(".log", ".2.log"))
    Assert.assertEquals(message3 + message4, Files.readString(logPath1))
    Assert.assertEquals(message1 + message2, Files.readString(logPath2))
  }
}