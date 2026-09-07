// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.VMOptions.MemoryKind
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.UnhandledException
import com.intellij.openapi.diagnostic.UnhandledExceptionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WRAPPER_NAME = "com.intellij.openapi.diagnostic.UnhandledException"

/**
 * An [UnhandledException] carries no useful information. Every writer must show the real cause.
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
  fun `an out of memory error keeps its kind inside the wrapper`() {
    val cause = OutOfMemoryError("Metaspace")
    assertEquals(MemoryKind.METASPACE, DefaultIdeaErrorLogger.getOOMErrorKind(cause))
    assertEquals("The wrapper must not hide the kind",
                 MemoryKind.METASPACE,
                 DefaultIdeaErrorLogger.getOOMErrorKind(UnhandledException(cause, isInteractive = true)))
  }
}
