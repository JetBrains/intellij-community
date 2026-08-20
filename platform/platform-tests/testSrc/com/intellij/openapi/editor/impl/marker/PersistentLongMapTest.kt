// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
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
  fun `put supports non-negative keys and preserves previous versions`(implementation: PersistentLongMapImplementation) {
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
        assertEquals(expected, version.getUnchecked(key), "$implementation, unchecked version $versionIndex, key $key")
      }
    }
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `put replaces a value only in the new version`(implementation: PersistentLongMapImplementation) {
    val original = PersistentLongMap.empty<String>(implementation)
      .put(42, "old")
      .put(84, "untouched")
    val updated = original.put(42, "new")

    assertEquals("old", original[42])
    assertEquals("untouched", original[84])
    assertEquals("new", updated[42])
    assertEquals("untouched", updated[84])
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `operations reject negative keys`(implementation: PersistentLongMapImplementation) {
    val map = PersistentLongMap.empty<String>(implementation)

    for (key in longArrayOf(-1, Long.MIN_VALUE)) {
      assertThrows(IllegalArgumentException::class.java) { map[key] }
      assertThrows(IllegalArgumentException::class.java) { map.put(key, "value") }
      assertThrows(IllegalArgumentException::class.java) { map.remove(key) }
    }
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
  fun `builder batches changes and preserves published versions`(implementation: PersistentLongMapImplementation) {
    val original = PersistentLongMap.empty<String>(implementation)
      .put(1, "one")
      .put(128, "one hundred twenty eight")
    val builder = original.builder()

    builder.put(1, "updated one")
    builder.put(2, "two")
    builder.put(256, "two hundred fifty six")
    builder.remove(128)
    assertEquals("updated one", builder.getUnchecked(1))
    assertEquals("two", builder.getUnchecked(2))
    val updated = builder.build()

    assertEquals("one", original[1])
    assertEquals("one hundred twenty eight", original[128])
    assertNull(original[2])
    assertNull(original[256])
    assertEquals("updated one", updated[1])
    assertEquals("two", updated[2])
    assertNull(updated[128])
    assertEquals("two hundred fifty six", updated[256])

    val nextBuilder = updated.builder()
    nextBuilder.put(1, "next one")
    nextBuilder.remove(2)
    val next = nextBuilder.build()

    assertEquals("updated one", updated[1])
    assertEquals("two", updated[2])
    assertEquals("next one", next[1])
    assertNull(next[2])
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `builder rejects negative keys and cannot be reused after build`(implementation: PersistentLongMapImplementation) {
    val map = PersistentLongMap.empty<String>(implementation)
    val builder = map.builder()

    for (key in longArrayOf(-1, Long.MIN_VALUE)) {
      assertThrows(IllegalArgumentException::class.java) { builder[key] }
      assertThrows(IllegalArgumentException::class.java) { builder.put(key, "value") }
      assertThrows(IllegalArgumentException::class.java) { builder.remove(key) }
    }

    assertSame(map, builder.build())
    assertThrows(IllegalStateException::class.java) { builder[0] }
    assertThrows(IllegalStateException::class.java) { builder.put(0, "value") }
    assertThrows(IllegalStateException::class.java) { builder.remove(0) }
    assertThrows(IllegalStateException::class.java) { builder.build() }
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `random builder batches agree with a mutable map`(implementation: PersistentLongMapImplementation) {
    val random = Random(0xB01D + implementation.ordinal)
    val randomKeys = LongArray(128) { random.nextLong().ushr(1) }
    val keys = KEYS + randomKeys
    val expected = mutableMapOf<Long, Int>()
    var actual = PersistentLongMap.empty<Int>(implementation)

    repeat(100) { batch ->
      val builder = actual.builder()
      repeat(20) { operation ->
        val key = keys[random.nextInt(keys.size)]
        if (random.nextInt(3) == 0) {
          expected.remove(key)
          builder.remove(key)
        }
        else {
          val value = batch * 20 + operation
          expected[key] = value
          builder.put(key, value)
        }
      }
      actual = builder.build()

      repeat(8) {
        val probe = keys[random.nextInt(keys.size)]
        assertEquals(expected[probe], actual[probe], "$implementation, batch $batch, key $probe")
      }
    }

    for (key in keys) {
      assertEquals(expected[key], actual[key], "$implementation, final key $key")
    }
  }

  @ParameterizedTest
  @EnumSource(value = PersistentLongMapImplementation::class, names = ["CHAMP", "CHAMP_64"])
  fun `champ layouts separate shared hash prefixes and compact nodes after removal`(implementation: PersistentLongMapImplementation) {
    val keyCount = 4_096
    var map = PersistentLongMap.empty<Int>(implementation)

    // A dense set much larger than the widest root forces repeated entry-to-child promotion at several trie levels.
    repeat(keyCount) { key ->
      map = map.put(key.toLong(), key)
    }
    val populated = map

    for (key in 0 until keyCount step 2) {
      map = map.remove(key.toLong())
    }
    repeat(keyCount) { key ->
      assertEquals(key, populated[key.toLong()], "published version, key $key")
      assertEquals(if (key and 1 == 0) null else key, map[key.toLong()], "partially removed version, key $key")
    }

    for (key in 1 until keyCount step 2) {
      map = map.remove(key.toLong())
    }
    repeat(keyCount) { key ->
      assertNull(map[key.toLong()], "fully removed version, key $key")
    }
  }

  @ParameterizedTest
  @EnumSource(PersistentLongMapImplementation::class)
  fun `random updates agree with a mutable map`(implementation: PersistentLongMapImplementation) {
    val random = Random(0x5EED + implementation.ordinal)
    val randomKeys = LongArray(128) { random.nextLong().ushr(1) }
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
