// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.random.Random

@Suppress("INVISIBLE_REFERENCE")
internal class PersistentLongMapTest {
  @Test
  fun `empty returns one shared instance per implementation`() {
    val stringMaps = PersistentLongMapImplementation.entries.associateWith { PersistentLongMap.empty<String>(it) }
    val intMaps = PersistentLongMapImplementation.entries.associateWith { PersistentLongMap.empty<Int>(it) }

    for (implementation in PersistentLongMapImplementation.entries) {
      @Suppress("AssertBetweenInconvertibleTypes")
      assertSame(stringMaps.getValue(implementation), intMaps.getValue(implementation), implementation.name)
    }

    for ((index, implementation) in PersistentLongMapImplementation.entries.withIndex()) {
      for (otherImplementation in PersistentLongMapImplementation.entries.drop(index + 1)) {
        assertNotSame(
          stringMaps.getValue(implementation),
          stringMaps.getValue(otherImplementation),
          "$implementation and $otherImplementation"
        )
      }
    }
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `put supports the full signed key range and preserves previous versions`(implementation: PersistentLongMapImplementation) {
    var map: PersistentLongMap<String> = PersistentLongMap.empty<String>(implementation)
    val versions = mutableListOf<PersistentLongMap<String>>(map)

    for ((index, key) in KEYS.withIndex()) {
      map = map.put(key, value(index))
      versions.add(map)
    }

    for ((versionIndex, version) in versions.withIndex()) {
      for ((keyIndex, key) in KEYS.withIndex()) {
        val expected = if (keyIndex < versionIndex) value(keyIndex) else null
        assertEquals(expected, version[key], "$implementation, version $versionIndex, key $key")
      }
    }
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `put replaces a value only in the new version`(implementation: PersistentLongMapImplementation) {
    val original = PersistentLongMap.empty<String>(implementation)
      .put(42, "old")
      .put(-42, "untouched")
    val updated = original.put(42, "new")

    assertEquals("old", original[42])
    assertEquals("untouched", original[-42])
    assertEquals("new", updated[42])
    assertEquals("untouched", updated[-42])
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `remove preserves remaining entries and previous versions`(implementation: PersistentLongMapImplementation) {
    var map = PersistentLongMap.empty<String>(implementation)
    for ((index, key) in KEYS.withIndex()) {
      map = map.put(key, value(index))
    }

    for ((removedIndex, removedKey) in KEYS.withIndex()) {
      val previous = map
      map = map.remove(removedKey)

      assertEquals(value(removedIndex), previous[removedKey], "$implementation, previous version, key $removedKey")
      assertNull(map[removedKey], "$implementation, removed key $removedKey")
      for (remainingIndex in removedIndex + 1 until KEYS.size) {
        val remainingKey = KEYS[remainingIndex]
        assertEquals(value(remainingIndex), map[remainingKey], "$implementation, remaining key $remainingKey")
      }
    }

    for (key in KEYS) {
      assertNull(map[key], "$implementation, key $key")
    }
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `removing an absent key reuses the current version`(implementation: PersistentLongMapImplementation) {
    val empty = PersistentLongMap.empty<String>(implementation)
    val populated = empty.put(1, "one")

    assertSame(empty, empty.remove(1))
    assertSame(populated, populated.remove(2))
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `random updates agree with a mutable map`(implementation: PersistentLongMapImplementation) {
    val random = Random(0x5EED + implementation.ordinal)
    val randomKeys = LongArray(128) { random.nextLong() }
    val keys = KEYS + randomKeys
    val expected = mutableMapOf<Long, Int>()
    var actual = PersistentLongMap.empty<Int>(implementation)

    repeat(1_000) { operation ->
      val key = keys[random.nextInt(keys.size)]
      if (random.nextInt(3) == 0) {
        expected.remove(key)
        actual = actual.remove(key)
      }
      else {
        expected[key] = operation
        actual = actual.put(key, operation)
      }

      repeat(8) {
        val probe = keys[random.nextInt(keys.size)]
        assertEquals(expected[probe], actual[probe], "$implementation, operation $operation, key $probe")
      }
    }

    for (key in keys) {
      assertEquals(expected[key], actual[key], "$implementation, final key $key")
    }
  }

  private fun value(index: Int): String = "value-$index"

  companion object {
    private val KEYS = longArrayOf(
      Long.MIN_VALUE,
      Long.MIN_VALUE + 1,
      -4097,
      -4096,
      -1025,
      -1024,
      -257,
      -256,
      -129,
      -128,
      -65,
      -64,
      -33,
      -32,
      -17,
      -16,
      -2,
      -1,
      0,
      1,
      15,
      16,
      31,
      32,
      63,
      64,
      127,
      128,
      255,
      256,
      1023,
      1024,
      4095,
      4096,
      Long.MAX_VALUE - 1,
      Long.MAX_VALUE,
    )
  }
}
