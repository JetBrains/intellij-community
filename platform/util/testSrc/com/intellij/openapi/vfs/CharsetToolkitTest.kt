// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.vfs.CharsetToolkit.GuessedEncoding
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.IntDistribution
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.charset.StandardCharsets.UTF_8

private const val PROPERTY_ITERATIONS = 1_000

class CharsetToolkitTest {
  @Test
  fun `classifies ASCII and binary input`() {
    assertThat(guess("plain text".toByteArray()))
      .describedAs("ASCII text")
      .isEqualTo(GuessedEncoding.SEVEN_BIT)
    assertThat(guess(byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte())))
      .describedAs("an input with a binary byte")
      .isEqualTo(GuessedEncoding.BINARY)
  }

  @ParameterizedTest
  @ValueSource(ints = [2, 3, 4, 5, 6])
  fun `recognizes supported multibyte sequences`(byteCount: Int) {
    assertThat(guess(multibyteSequence(byteCount)))
      .describedAs("a $byteCount-byte sequence")
      .isEqualTo(GuessedEncoding.VALID_UTF8)
  }

  @ParameterizedTest
  @ValueSource(ints = [2, 3, 4, 5, 6])
  fun `rejects an invalid continuation byte`(byteCount: Int) {
    val bytes = multibyteSequence(byteCount)
    bytes[bytes.lastIndex] = 'x'.code.toByte()

    assertThat(guess(bytes))
      .describedAs("a $byteCount-byte sequence with an invalid continuation byte")
      .isEqualTo(GuessedEncoding.INVALID_UTF8)
  }

  @ParameterizedTest
  @ValueSource(ints = [2, 3, 4, 5, 6])
  fun `accepts a sequence truncated at the range end`(byteCount: Int) {
    val bytes = multibyteSequence(byteCount).copyOf(byteCount - 1)

    assertThat(guess(bytes))
      .describedAs("a truncated $byteCount-byte sequence")
      .isEqualTo(GuessedEncoding.VALID_UTF8)
  }

  @ParameterizedTest
  @ValueSource(ints = [6, 7, 8, 14, 15, 16])
  fun `recognizes a sequence across eight-byte boundaries`(offset: Int) {
    val sequence = multibyteSequence(4)
    val bytes = ByteArray(offset + sequence.size) { 'x'.code.toByte() }
    sequence.copyInto(bytes, offset)

    assertThat(guess(bytes))
      .describedAs("a sequence at offset $offset")
      .isEqualTo(GuessedEncoding.VALID_UTF8)
  }

  @Test
  fun `uses only the requested range`() {
    val bytes = byteArrayOf(0, 'a'.code.toByte(), 'b'.code.toByte(), 0)

    assertThat(CharsetToolkit.guessFromContent(bytes, 1, 3))
      .describedAs("an ASCII range between binary bytes")
      .isEqualTo(GuessedEncoding.SEVEN_BIT)
  }

  @Test
  fun `binary input takes precedence over invalid UTF-8`() {
    assertThat(guess(byteArrayOf(0xFF.toByte(), 0)))
      .describedAs("an invalid UTF-8 byte followed by a binary byte")
      .isEqualTo(GuessedEncoding.BINARY)
  }

  @Test
  fun `valid text is classified by its generated characters`() {
    checkProperty(validTextBytes()) { bytes ->
      val expected = if (bytes.any { it < 0 }) GuessedEncoding.VALID_UTF8 else GuessedEncoding.SEVEN_BIT
      assertThat(guess(bytes))
        .describedAs("valid generated text")
        .isEqualTo(expected)
    }
  }

  @Test
  fun `a generated binary byte makes valid text binary`() {
    val binaryInput = Generator.zipWith(validTextBytes(), Generator.integers(0, CharsetToolkit.BINARY_THRESHOLD - 1)) { bytes, binaryByte ->
      insert(bytes, byteArrayOf(binaryByte.toByte()))
    }

    checkProperty(binaryInput) { bytes ->
      assertThat(guess(bytes))
        .describedAs("valid generated text with a binary byte")
        .isEqualTo(GuessedEncoding.BINARY)
    }
  }

  @Test
  fun `an invalid UTF-8 sequence makes valid text invalid`() {
    val invalidInput = validTextBytes().map { bytes ->
      insert(bytes, byteArrayOf(0xC2.toByte(), ' '.code.toByte()))
    }

    checkProperty(invalidInput) { bytes ->
      assertThat(guess(bytes))
        .describedAs("valid generated text with an invalid UTF-8 sequence")
        .isEqualTo(GuessedEncoding.INVALID_UTF8)
    }
  }

  private fun guess(bytes: ByteArray): GuessedEncoding = CharsetToolkit.guessFromContent(bytes, 0, bytes.size)

  private fun multibyteSequence(byteCount: Int): ByteArray {
    val firstByte = when (byteCount) {
      2 -> 0xC2
      3 -> 0xE2
      4 -> 0xF0
      5 -> 0xF8
      6 -> 0xFC
      else -> error("Unsupported byte count: $byteCount")
    }
    return ByteArray(byteCount) { index -> if (index == 0) firstByte.toByte() else 0x80.toByte() }
  }

  private fun validTextBytes(): Generator<ByteArray> {
    val codePoint = Generator.anyOf(
      Generator.integers(0x20, 0x7E),
      Generator.integers(0x80, 0x7FF),
      Generator.integers(0x800, 0xD7FF),
      Generator.integers(0xE000, 0xFFFF),
      Generator.integers(0x10000, Character.MAX_CODE_POINT),
    )
    return Generator.listsOf(IntDistribution.uniform(0, 100), codePoint).map { codePoints ->
      buildString {
        codePoints.forEach(::appendCodePoint)
      }.toByteArray(UTF_8)
    }
  }

  private fun insert(bytes: ByteArray, inserted: ByteArray): ByteArray {
    val offset = bytes.size / 2
    return ByteArray(bytes.size + inserted.size).also { result ->
      bytes.copyInto(result, endIndex = offset)
      inserted.copyInto(result, destinationOffset = offset)
      bytes.copyInto(result, destinationOffset = offset + inserted.size, startIndex = offset)
    }
  }

  private fun checkProperty(generator: Generator<ByteArray>, assertion: (ByteArray) -> Unit) {
    PropertyChecker.customized().withIterationCount(PROPERTY_ITERATIONS).forAll(generator) { bytes ->
      assertion(bytes)
      true
    }
  }
}
