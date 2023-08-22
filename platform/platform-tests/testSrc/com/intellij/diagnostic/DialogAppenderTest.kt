// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.ErrorLogger
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.junit.Assert.*
import org.junit.Test

/**
 * @author kir
 */
class DialogAppenderTest : BareTestFixtureTestCase() {
  @Test fun testIdeaLoggerMessage() {
    val appender = DialogAppender()
    val event = IdeaLoggingEvent("message", Throwable())
    val logger = MyErrorLogger(true)
    appender.appendToLoggers(event, arrayOf<ErrorLogger>(logger))
    processEvents(appender)
    assertNotNull(logger.handled)
    assertEquals(event.message, logger.handled?.message)
    assertSame(event.throwable, logger.handled?.throwable)
  }

  @Test fun testAppendersOrder() {
    val appender = DialogAppender()
    val event = IdeaLoggingEvent("message", Throwable())
    val logger1 = MyErrorLogger(true)
    val logger2 = MyErrorLogger(true)
    val logger3 = MyErrorLogger(false)
    appender.appendToLoggers(event, arrayOf<ErrorLogger>(logger1, logger2))
    processEvents(appender)
    assertNull(logger3.handled)
    assertNotNull(logger2.handled)
    assertNull("loggers should be called in a reverse order", logger1.handled)
  }

  private fun processEvents(appender: DialogAppender) {
    @Suppress("ControlFlowWithEmptyBody")
    while (appender.dialogRunnable != null);
  }

  private class MyErrorLogger(private val canHandle: Boolean) : ErrorLogger {
    var handled: IdeaLoggingEvent? = null
      private set

    override fun canHandle(event: IdeaLoggingEvent) = canHandle

    override fun handle(event: IdeaLoggingEvent) {
      this.handled = event
    }
  }
}
