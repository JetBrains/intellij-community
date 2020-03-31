// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm

import com.intellij.execution.testframework.sm.runner.cutLineIfTooLong
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import org.junit.Assert
import org.junit.Test


class LongLineCutterTest {
  private fun createMessage(attrs:Map<String, String>) = ServiceMessage.asString(
    "myMessage",
    attrs + mapOf("name" to "testFailed")
  )
  @Test
  fun shortMessageUntouched() {
    val message = createMessage(mapOf(
      "A" to "B",
      "Z" to "Q"
    ))
    Assert.assertEquals(message, cutLineIfTooLong(message, Int.MAX_VALUE, 100))
  }

  @Test
  fun longLineShortened() {
    val maxLength = 10000
    val text = cutLineIfTooLong("abcde".repeat(maxLength), maxLength, 100)
    Assert.assertEquals(text.length, maxLength)
  }

  @Test
  fun actualExpectedShort() {
    val maxLength = 1000
    val message = createMessage(mapOf(
      "expected" to "A".repeat(maxLength * 2),
      "actual" to "B"
    ))
    val result = ServiceMessage.parse(cutLineIfTooLong(message, maxLength, 10))!!

    val actual = result.attributes["actual"]!!
    val expected = result.attributes["expected"]!!

    Assert.assertEquals(actual, "B")
    Assert.assertTrue(expected.startsWith("A"))
    Assert.assertTrue(expected.endsWith("A"))
    Assert.assertTrue("..." in expected)

  }

  @Test
  fun actualExpectedLong() {
    val maxLength = 1000
    val message = createMessage(mapOf(
      "expected" to "A".repeat(maxLength * 2),
      "actual" to "B".repeat(maxLength * 2)
    ));
    val result = ServiceMessage.parse(cutLineIfTooLong(message, maxLength, 10))!!

    val actual = result.attributes["actual"]!!
    val expected = result.attributes["expected"]!!

    Assert.assertTrue(actual.startsWith("B"))
    Assert.assertTrue(actual.endsWith("B"))
    Assert.assertTrue(expected.startsWith("A"))
    Assert.assertTrue(expected.endsWith("A"))
    Assert.assertTrue(expected.length == actual.length)
    Assert.assertTrue("..." in actual)
    Assert.assertTrue("..." in expected)

  }

  @Test
  fun longMessageShortened() {
    val maxLength = 10000
    val s = "abc\r\n"
    val longString = s.repeat(maxLength * 2)
    val message = createMessage(mapOf(
      "A" to "B",
      "C" to "D",
      "Z" to longString
    ))
    val result = cutLineIfTooLong(message, maxLength, 100)
    Assert.assertTrue("Failed to cut message", result.length <= maxLength)
    val shortenedMessage = ServiceMessage.parse(result)!!
    Assert.assertEquals("B", shortenedMessage.attributes["A"])
    Assert.assertEquals("D", shortenedMessage.attributes["C"])
    val longestValue = shortenedMessage.attributes["Z"]!!
    Assert.assertTrue("D", longestValue.startsWith(s) && longestValue.endsWith(s))
  }
}