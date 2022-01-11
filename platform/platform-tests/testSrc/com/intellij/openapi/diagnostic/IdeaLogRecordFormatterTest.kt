// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import org.junit.Assert
import org.junit.Test
import java.util.logging.Level
import java.util.logging.LogRecord

class IdeaLogRecordFormatterTest {
  @Test
  fun testThrowable() {
    val throwable = Throwable()
    val logRecord = LogRecord(Level.INFO, "Foo")
    logRecord.thrown = throwable
    val formatter = IdeaLogRecordFormatter()
    val formatted = formatter.format(logRecord)
    Assert.assertTrue("IdeaLogRecordFormatterTest" in formatted)
    Assert.assertTrue(formatted.endsWith(System.lineSeparator()))
  }
}