// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for the plain-text (de)serialization of the freeze common stacktrace stored in the `.throwable-stack` report file,
 * see [serializeStackTrace] and [deserializeStackTrace].
 */
class FreezeStackTraceSerializationTest {
  @Test
  fun `round trip preserves every frame field`() {
    val stackTrace = arrayOf(
      StackTraceElement("com.example.Outer", "outerMethod", "Outer.java", 42),
      StackTraceElement("com.example.Inner.Nested", "run", "Inner.kt", 7),
      StackTraceElement("com.example.Native", "nativeMethod", null, -2),
    )

    val restored = deserializeStackTrace(serializeStackTrace(stackTrace))

    assertThat(restored).containsExactly(*stackTrace)
  }

  @Test
  fun `serialized form is one tab-separated line per frame`() {
    val stackTrace = arrayOf(
      StackTraceElement("com.example.A", "a", "A.java", 1),
      StackTraceElement("com.example.B", "b", "B.java", 2),
    )

    assertThat(serializeStackTrace(stackTrace)).isEqualTo(
      "com.example.A\ta\tA.java\t1\n" +
      "com.example.B\tb\tB.java\t2"
    )
  }

  @Test
  fun `null file name is round-tripped as null`() {
    val frame = StackTraceElement("com.example.NoFile", "m", null, 10)

    val restored = deserializeStackTrace(serializeStackTrace(arrayOf(frame)))

    assertThat(restored).hasSize(1)
    assertThat(restored[0].fileName).isNull()
    assertThat(restored[0].lineNumber).isEqualTo(10)
  }

  @Test
  fun `empty stacktrace serializes to empty string and deserializes to empty list`() {
    assertThat(serializeStackTrace(emptyArray())).isEmpty()
    assertThat(deserializeStackTrace("")).isEmpty()
  }

  @Test
  fun `blank lines are ignored during deserialization`() {
    val text = "com.example.A\ta\tA.java\t1\n\n   \ncom.example.B\tb\tB.java\t2\n"

    val restored = deserializeStackTrace(text)

    assertThat(restored).containsExactly(
      StackTraceElement("com.example.A", "a", "A.java", 1),
      StackTraceElement("com.example.B", "b", "B.java", 2),
    )
  }

  @Test
  fun `malformed lines with too few fields are skipped`() {
    val text = "com.example.A\ta\tA.java\t1\n" +
               "broken-line-without-tabs\n" +
               "com.example.B\tb\tB.java\t2"

    val restored = deserializeStackTrace(text)

    assertThat(restored).containsExactly(
      StackTraceElement("com.example.A", "a", "A.java", 1),
      StackTraceElement("com.example.B", "b", "B.java", 2),
    )
  }

  @Test
  fun `non-numeric line number falls back to minus one`() {
    val text = "com.example.A\ta\tA.java\tNOT_A_NUMBER"

    val restored = deserializeStackTrace(text)

    assertThat(restored).hasSize(1)
    assertThat(restored[0].lineNumber).isEqualTo(-1)
  }
}
