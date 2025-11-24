// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.PinyinMatcher
import org.junit.jupiter.api.Disabled
import java.net.URL
import java.text.Normalizer
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Generates data arrays for [PinyinMatcher] using Unihan_Readings.txt file from unicode.org
 * Requires Internet connection.
 */
@Disabled("It's a generator, not a real test")
class PinyinMatcherDataTest {
  @Test
  fun ensurePinyinDataIsUpToDate() {
    val mappings = readMappings()
    val initials = generateInitials(mappings)
    val encodingStr = initials.joinToString(",")
    val data = getDataString(mappings, initials)

    val message = """
       Pinyin data mismatch. Please update constants in ${PinyinMatcher::class.qualifiedName} to the following:
       ${toKotlinStringLiteral("ENCODING", encodingStr)}
       ${toKotlinStringLiteral("DATA", data)}
     """.trimIndent()

    assertEquals(PinyinMatcher.ENCODING, encodingStr, message)
    assertEquals(PinyinMatcher.DATA, data, message)
  }

  companion object {
    private const val LINE_LENGTH = 100
    private const val DATA_SOURCE = "https://unicode.org/Public/UNIDATA/Unihan.zip"
    private const val READINGS_FILE = "Unihan_Readings.txt"

    private val U4_UPPER = HexFormat {
      upperCase = true
      number {
        minLength = 4
        removeLeadingZeros = true
      }
    }

    private fun toKotlinStringLiteral(varName: String?, input: String): String {
      val result = StringBuilder("@ApiStatus.Internal\nval $varName: String =\n\"")
      var curLineLength = 0
      input.forEachIndexed { i, ch ->
        val charRepresentation = when {
          ch == '$' || ch == '"' || ch == '\\' -> {
            "\\" + ch
          }
          ch.code < 127 -> {
            ch.toString()
          }
          else -> {
            unicodeEscapeCodePoint(ch.code)
          }
        }
        result.append(charRepresentation)
        curLineLength += charRepresentation.length
        if (curLineLength > LINE_LENGTH && i < input.length - 1) {
          curLineLength = 0
          result.append("\" +\n\"")
        }
      }
      return result.append("\"").toString()
    }

    internal const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
    private const val HIGH_SURROGATE_ENCODE_OFFSET = (Char.MIN_HIGH_SURROGATE.code - (MIN_SUPPLEMENTARY_CODE_POINT ushr 10))

    fun unicodeEscapeCodePoint(cp: Int): String {
      if (cp <= 0xFFFF) return "\\u" + cp.toHexString(U4_UPPER)
      val u = cp - 0x10000
      val hi = (u ushr 10) + HIGH_SURROGATE_ENCODE_OFFSET
      val lo = (u and 0x3FF) + Char.MIN_LOW_SURROGATE.code
      return "\\u" + hi.toHexString(U4_UPPER) + "\\u" + lo.toHexString(U4_UPPER)
    }

    private fun getDataString(mappings: List<Mapping>, initials: List<String>): String {
      val encoding = initials.associateWith { PinyinMatcher.BASE_CHAR + initials.indexOf(it) }
      val map = mappings.associate { mapping -> mapping.codePoint to encoding[mapping.charString()] }
      val lastCodePoint = mappings.maxOf(Mapping::codePoint)
      return (PinyinMatcher.BASE_CODE_POINT..lastCodePoint).map { map[it] ?: ' ' }.joinToString("")
    }

    private fun generateInitials(mappings: List<Mapping>): List<String> {
      return mappings.map(Mapping::charString).distinct().sortedWith(compareBy(String::length).then(naturalOrder()))
    }

    private fun readMappings(): List<Mapping> {
      ZipInputStream(URL(DATA_SOURCE).openStream()).use { zis ->
        while (true) {
          val entry = zis.nextEntry
          requireNotNull(entry) { "No $READINGS_FILE found inside $DATA_SOURCE" }
          if (entry.name == READINGS_FILE) {
            return zis.bufferedReader(Charsets.UTF_8).lineSequence()
              .mapNotNull(Mapping::parseUniHan)
              .groupBy(Mapping::codePoint)
              .map { (_, mappings) -> mappings.reduce { acc, mapping -> Mapping.merge(acc, mapping) } }
          }
        }
      }
    }
  }

  private data class Mapping(val codePoint: Int, val chars: Long) {
    fun charString(): String {
      return oneBitsSequence(chars).joinToString("") { bit -> (bit + 'a'.code).toChar().toString() }
    }

    private fun oneBitsSequence(n: Long): Sequence<Int> = sequence {
      var bits = n
      while (bits != 0L) {
        val lsbIndex = bits.countTrailingZeroBits()
        yield(lsbIndex)
        bits = bits and (bits - 1)
      }
    }

    override fun toString(): String {
      return "${codePoint.toHexString(U4_UPPER)}: ${charString()}"
    }

    companion object {
      private val whitespaceRegex = "\\s+".toRegex()
      fun merge(m1: Mapping, m2: Mapping): Mapping {
        require(m1.codePoint == m2.codePoint)
        return Mapping(m1.codePoint, m1.chars or m2.chars)
      }

      fun parseUniHan(line: String): Mapping? {
        if (line.startsWith('#')) return null
        val parts = line.split(whitespaceRegex)
        if (parts.size != 3) return null
        if (!parts.first().startsWith("U+")) return null
        val codePoint = parts.first().substring(2).toInt(16)
        if (codePoint < PinyinMatcher.BASE_CODE_POINT) return null
        // Codepoints outside BMP are not supported for now
        if (codePoint > 0xA000) return null
        val readings = when (parts[1]) {
          "kMandarin" -> listOf(parts[2])
          "kHanyuPinyin" -> {
            val colonPos = parts[2].indexOf(':')
            if (colonPos == -1) return null
            parts[2].substring(colonPos + 1).split(',')
          }
          else -> {
            return null
          }
        }
        var encoded: Long = 0
        for (reading in readings) {
          val initial = Normalizer.normalize(reading, Normalizer.Form.NFKD)[0]
          encoded = encoded or (1L shl (initial.code - 'a'.code))
        }
        return Mapping(codePoint, encoded)
      }
    }
  }
}