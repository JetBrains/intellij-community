// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.random.Random
import kotlin.test.assertEquals

@RunWith(JUnit4::class)
class IndexingStampInfoTest {
  private data class IndexingStampInfoTemplate(private val indexingFileStamp: Long,
                                               private val indexingByteLength: Long,
                                               private val indexingCharLength: Int,
                                               private val isBinary: Boolean) {
    val maxTimestamp: Long = 0x0ff_ff_ff_ff_ff_ffL // 48 bits
    val maxByteLength: Int = 0x7f_ff_ff_ff;
    fun toInfoObject(): IndexingStampInfo {
      return IndexingStampInfo(indexingFileStamp, indexingByteLength, indexingCharLength, isBinary)
    }

    fun toDeserializedInfoObject(): IndexingStampInfo {
      val deserializedBytesLength = indexingByteLength.coerceAtMost(maxByteLength.toLong())
      val deserializedCharLength = if (isBinary) {
        -1
      }
      else {
        indexingCharLength.toLong()
          .coerceIn(deserializedBytesLength + Short.MIN_VALUE, deserializedBytesLength + Short.MAX_VALUE)
          .coerceIn(0, Int.MAX_VALUE.toLong())
          .toInt()
      }

      return IndexingStampInfo(indexingFileStamp and 0x0_ff_ff_ff_ff_ff_ffL /* 48 bits */,
                               deserializedBytesLength,
                               deserializedCharLength,
                               isBinary)
    }
  }

  @Test
  fun testToInt3() {
    val seed = System.currentTimeMillis()
    println("Seed: $seed")
    val rnd = Random(seed)
    val interestingIntValues: List<Int> = listOf(0, 42, 0x0_7f_ff, 0x0_ff_ff, 0x01_ff_ff, 0x7f_ff_ff_ff) +
                                          (0..50).map { rnd.nextInt(0, Int.MAX_VALUE) }

    val interestingLongValues: List<Long> = interestingIntValues.map(Int::toLong) +
                                            listOf(0x0_ff_ff_ff_ff, 0x0_ff_ff_ff_ff_ff_ff /* 48 bits */,
                                                   0x01_ff_ff_ff_ff_ff_ff, 0x7f_ff_ff_ff_ff_ff_ff,
                                                   Long.MAX_VALUE) +
                                            (0..50).map { rnd.nextLong(0, Long.MAX_VALUE) }

    val interestingBoolValues = listOf(true, false)

    interestingLongValues.forEach { indexingFileStamp ->
      interestingLongValues.forEach { indexingByteLength ->
        interestingIntValues.forEach { indexingCharLength ->
          interestingBoolValues.forEach { isBinary ->
            val validIndexingCharLength = if (isBinary) -1 else indexingCharLength
            val template = IndexingStampInfoTemplate(indexingFileStamp, indexingByteLength, validIndexingCharLength, isBinary)
            val int3 = template.toInfoObject().toInt3()
            val deserialized = IndexingStampInfo.fromInt3(int3)
            val expected = template.toDeserializedInfoObject()
            assertEquals(expected, deserialized, "template: $template")
          }
        }
      }
    }
  }
}