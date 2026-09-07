// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.VMOptions.MemoryKind
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.UnhandledException
import com.intellij.openapi.diagnostic.UnhandledExceptionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WRAPPER_NAME = "com.intellij.openapi.diagnostic.UnhandledException"

/**
 * An [UnhandledException] carries no useful information. Every writer and every reporter must show the real cause.
 * See IJPL-254578.
 */
internal class UnhandledExceptionUnwrapTest {

  private class TestCause(message: String) : RuntimeException(message)

  @Test
  fun `unwrap returns the cause and the kind`() {
    val cause = TestCause("boom")

    val interactive = UnhandledException.unwrapIfUnhandled(UnhandledException(cause, isInteractive = true))
    assertSame(cause, interactive.realCause)
    assertEquals(UnhandledExceptionKind.INTERACTIVE, interactive.unhandledExceptionKind)

    val background = UnhandledException.unwrapIfUnhandled(UnhandledException(cause, isInteractive = false))
    assertSame(cause, background.realCause)
    assertEquals(UnhandledExceptionKind.BACKGROUND, background.unhandledExceptionKind)
  }

  @Test
  fun `unwrap keeps a throwable that is not wrapped`() {
    val cause = TestCause("boom")
    val unwrapped = UnhandledException.unwrapIfUnhandled(cause)
    assertSame(cause, unwrapped.realCause)
    assertEquals("A plain throwable is handled", UnhandledExceptionKind.HANDLED, unwrapped.unhandledExceptionKind)
  }

  @Test
  fun `the log writer shows the real cause`() {
    val cause = TestCause("boom")
    val text = IdeaLogRecordFormatter.formatThrowable(UnhandledException(cause, isInteractive = true))

    assertTrue("The text must start with the real cause, but was: $text", text.startsWith(TestCause::class.java.name))
    assertFalse("The text must not name the wrapper, but was: $text", WRAPPER_NAME in text)
    assertEquals("The text must equal the text of the cause", IdeaLogRecordFormatter.formatThrowable(cause), text)
  }

  @Test
  fun `the error report shows the real cause`() {
    val cause = TestCause("boom")
    val event = IdeaLoggingEvent("message", UnhandledException(cause, isInteractive = true))

    assertTrue("The report must start with the real cause, but was: ${event.throwableText}",
               event.throwableText.startsWith(TestCause::class.java.name))
    assertFalse("The report must not name the wrapper, but was: ${event.throwableText}", WRAPPER_NAME in event.throwableText)
    assertEquals(IdeaLoggingEvent("message", cause).throwableText, event.throwableText)
  }

  /** An `ErrorReportSubmitter` reads the throwable of the event, so the event must drop the wrapper too. */
  @Test
  fun `the error report drops the wrapper and keeps the kind`() {
    val cause = TestCause("boom")
    val event = IdeaLoggingEvent("message", UnhandledException(cause, isInteractive = true))

    assertSame("The event must hand over the real cause", cause, event.throwable)
    assertEquals(UnhandledExceptionKind.INTERACTIVE, event.unhandledExceptionKind)
    assertEquals("An exception that was handled is handled",
                 UnhandledExceptionKind.HANDLED,
                 IdeaLoggingEvent("message", cause).unhandledExceptionKind)
  }

  @Test
  fun `the message pool entry shows the real cause`() {
    val cause = TestCause("boom")
    val message = LogMessage(UnhandledException(cause, isInteractive = false), "message", emptyList())

    assertTrue("The entry must start with the real cause, but was: ${message.throwableText}",
               message.throwableText.startsWith(TestCause::class.java.name))
    assertFalse("The entry must not name the wrapper, but was: ${message.throwableText}", WRAPPER_NAME in message.throwableText)
  }

  @Test
  fun `the message pool entry drops the wrapper`() {
    val cause = TestCause("boom")
    val entry = LogMessage(UnhandledException(cause, isInteractive = true), "message", emptyList())

    assertSame("The entry must hand over the real cause", cause, entry.throwable)
  }

  @Test
  fun `the message pool entry keeps the kind`() {
    val interactive = LogMessage(UnhandledException(TestCause("interactive"), isInteractive = true), "message", emptyList())
    val background = LogMessage(UnhandledException(TestCause("background"), isInteractive = false), "message", emptyList())
    val handled = LogMessage(TestCause("handled"), "message", emptyList())

    assertEquals(UnhandledExceptionKind.INTERACTIVE, interactive.unhandledExceptionKind)
    assertEquals(UnhandledExceptionKind.BACKGROUND, background.unhandledExceptionKind)
    assertEquals("An exception that was handled is handled", UnhandledExceptionKind.HANDLED, handled.unhandledExceptionKind)
  }

  /** [DefaultIdeaErrorLogger.findSubmitterByPluginInfo] reads this class, so the wrapper must not hide it. */
  @Test
  fun `the message pool entry hands over an abstract method error`() {
    val entry = LogMessage(UnhandledException(AbstractMethodError("boom"), isInteractive = true), "message", emptyList())

    assertTrue("The wrapper must not hide the class of the cause", entry.throwable is AbstractMethodError)
  }

  @Test
  fun `an out of memory error keeps its kind inside the wrapper`() {
    val cause = OutOfMemoryError("Metaspace")
    assertEquals(MemoryKind.METASPACE, DefaultIdeaErrorLogger.getOOMErrorKind(cause))
    assertEquals("The wrapper must not hide the kind",
                 MemoryKind.METASPACE,
                 DefaultIdeaErrorLogger.getOOMErrorKind(UnhandledException(cause, isInteractive = true)))
  }

  @Test
  fun `the dialog clusters a wrapped error with the same unwrapped error`() {
    val cause = TestCause("boom")
    val wrapped = LogMessage(UnhandledException(cause, isInteractive = true), "message", emptyList())
    val plain = LogMessage(cause, "message", emptyList())

    assertEquals("A wrapper must not make a separate cluster",
                 IdeErrorsDialog.hashMessage(plain),
                 IdeErrorsDialog.hashMessage(wrapped))
  }

  @Test
  fun `the dialog separates two different causes inside a wrapper`() {
    val first = LogMessage(UnhandledException(causeFromFirstFrame(), isInteractive = true), "message", emptyList())
    val second = LogMessage(UnhandledException(causeFromSecondFrame(), isInteractive = true), "message", emptyList())

    assertTrue("Two different causes must not share a cluster",
               IdeErrorsDialog.hashMessage(first) != IdeErrorsDialog.hashMessage(second))
  }

  // Two frames, one message. Only the stack tells the two causes apart.
  private fun causeFromFirstFrame(): Throwable = TestCause("boom")

  private fun causeFromSecondFrame(): Throwable = TestCause("boom")
}
