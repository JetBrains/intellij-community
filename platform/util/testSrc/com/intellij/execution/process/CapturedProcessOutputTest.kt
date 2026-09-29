package com.intellij.execution.process

import com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CapturedProcessOutputTest {
  @Test
  fun `attachment captures both streams and stops on disposal`() {
    val handler = NopProcessHandler().also { it.startNotify() }
    val output = CapturedProcessOutput()
    val capture = output.attachTo(handler)
    try {
      handler.notifyTextAvailable("out\n", ProcessOutputTypes.STDOUT)
      handler.notifyTextAvailable("error\n", ProcessOutputTypes.STDERR)
      handler.notifyTextAvailable("system\n", ProcessOutputTypes.SYSTEM)

      Disposer.dispose(capture)
      handler.notifyTextAvailable("after\n", ProcessOutputTypes.STDOUT)

      assertEquals("out\nerror\n", output.snapshot(null, 10).output)
    }
    finally {
      Disposer.dispose(capture)
      handler.destroyProcess()
    }
  }

  @Test
  fun `a late exit code updates stopped output without losing final text`() {
    val output = CapturedProcessOutput()
    output.append("first\n")
    output.markStopped(null)
    output.append("last\n")
    output.markStopped(17)
    output.markStopped(null)

    val snapshot = output.snapshot(null, 10)

    assertEquals("first\nlast\n", snapshot.output)
    assertEquals(false, snapshot.isRunning)
    assertEquals(17, snapshot.exitCode)
    assertEquals(CapturedProcessOutputStatus(false, 17), output.status)
  }

  @Test
  fun `appended fragments are indexed as complete lines`() {
    val output = CapturedProcessOutput()
    output.append("first")
    output.append("\r\nsecond\rthi")
    output.append("rd")

    val snapshot = output.snapshot(startLine = 1, maxLines = 2)

    assertEquals("second\rthird", snapshot.output)
    assertEquals(1, snapshot.startLine)
    assertEquals(3, snapshot.endLine)
    assertEquals(0, snapshot.availableStartLine)
    assertEquals(3, snapshot.availableEndLine)
  }

  @Test
  fun `line indexes stay stable when old lines are discarded`() {
    val output = CapturedProcessOutput(maxCapturedCharacters = 12)
    output.append("one\ntwo\nthree\n")

    val firstSnapshot = output.snapshot(startLine = null, maxLines = 10)

    assertEquals("two\nthree\n", firstSnapshot.output)
    assertEquals(1, firstSnapshot.startLine)
    assertEquals(3, firstSnapshot.endLine)
    assertEquals(1, firstSnapshot.availableStartLine)
    assertEquals(3, firstSnapshot.availableEndLine)

    output.append("four\n")
    val secondSnapshot = output.snapshot(startLine = 2, maxLines = 10)

    assertEquals("three\nfour\n", secondSnapshot.output)
    assertEquals(2, secondSnapshot.startLine)
    assertEquals(4, secondSnapshot.endLine)
    assertEquals(2, secondSnapshot.availableStartLine)
    assertEquals(4, secondSnapshot.availableEndLine)
    assertThrows(IllegalArgumentException::class.java) {
      output.snapshot(startLine = 1, maxLines = 10)
    }
  }

  @Test
  fun `oversized incomplete line is discarded`() {
    val output = CapturedProcessOutput(maxCapturedCharacters = 5)
    output.append("123456")
    output.append("ok")

    val snapshot = output.snapshot(startLine = null, maxLines = 10)

    assertEquals("ok", snapshot.output)
    assertEquals(1, snapshot.startLine)
    assertEquals(2, snapshot.endLine)
    assertEquals(1, snapshot.availableStartLine)
    assertEquals(2, snapshot.availableEndLine)
  }
}
