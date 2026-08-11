// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
internal class VersionedPayloadMapTest {

  @Test
  fun `ordered lower bound returns payload from greatest version not exceeding target`() {
    val first = Payload("first")
    val second = Payload("second")
    val map = VersionedPayloadMap.create(10, first, 20, second)

    assertNull(map.lowerBound(9))
    assertSame(first, map.lowerBound(10))
    assertSame(first, map.lowerBound(19))
    assertSame(second, map.lowerBound(20))
    assertSame(second, map.lowerBound(30))
  }

  @Test
  fun `create keeps entries ordered when versions are reversed`() {
    val first = Payload("first")
    val second = Payload("second")
    val map = VersionedPayloadMap.create(20, second, 10, first)

    assertNull(map.lowerBound(9))
    assertSame(first, map.lowerBound(10))
    assertSame(first, map.lowerBound(19))
    assertSame(second, map.lowerBound(20))
    assertSame(second, map.lowerBound(30))
  }

  @Test
  fun `ordered insert replaces latest version in place and appends newer version`() {
    val first = Payload("first")
    val second = Payload("second")
    val replacement = Payload("replacement")
    val appended = Payload("appended")
    val map = VersionedPayloadMap.create(10, first, 20, second)

    assertNull(map.insert(20, second))

    val replaced = map.insert(20, replacement)!!
    assertEquals(2, replaced.size())
    assertSame(first, replaced.lowerBound(19))
    assertSame(replacement, replaced.lowerBound(20))
    assertSame(replacement, replaced.lowerBound(25))

    val appendedMap = replaced.insert(30, appended)!!
    assertEquals(3, appendedMap.size())
    assertSame(replacement, appendedMap.lowerBound(29))
    assertSame(appended, appendedMap.lowerBound(30))
    assertSame(appended, appendedMap.lowerBound(35))
  }

  @Test
  fun `insert into empty map creates one entry map`() {
    val payload = Payload("payload")
    val map = VersionedPayloadMap.empty().insert(10, payload)!!

    assertEquals("VersionedPayloadMap1", map.javaClass.simpleName)
    assertEquals(1, map.size())
    assertSame(payload, map.lowerBound(20))
  }

  @Test
  fun `ordered map keeps null payload as explicit removal until newer version appears`() {
    val first = Payload("first")
    val second = Payload("second")
    val restored = Payload("restored")
    val map = VersionedPayloadMap.create(10, first, 20, second)

    val withRemoval = map.insert(30, null)!!
    assertSame(second, withRemoval.lowerBound(29))
    assertNull(withRemoval.lowerBound(30))
    assertNull(withRemoval.lowerBound(35))

    val withRestoredValue = withRemoval.insert(40, restored)!!
    assertNull(withRestoredValue.lowerBound(39))
    assertSame(restored, withRestoredValue.lowerBound(40))
    assertSame(restored, withRestoredValue.lowerBound(45))
  }

  @Test
  fun `explicitlyRemoved returns true only for exact removed version`() {
    val first = Payload("first")
    val second = Payload("second")
    val map = VersionedPayloadMap.create(10, first, 20, second)
      .insert(30, null)!!

    assertFalse(map.explicitlyRemoved(10))
    assertFalse(map.explicitlyRemoved(20))
    assertFalse(map.explicitlyRemoved(25))
    assertTrue(map.explicitlyRemoved(30))
    assertFalse(map.explicitlyRemoved(35))
  }

  @Test
  fun `explicitlyRemoved still reports older removed version after newer versions are inserted`() {
    val first = Payload("first")
    val second = Payload("second")
    val restored = Payload("restored")
    val map = VersionedPayloadMap.create(10, first, 20, second)
      .insert(30, null)!!
      .insert(40, restored)!!

    assertTrue(map.explicitlyRemoved(30))
    assertFalse(map.explicitlyRemoved(29))
    assertFalse(map.explicitlyRemoved(40))
    assertFalse(map.explicitlyRemoved(45))
  }

  @Test
  fun `middle insert returns new map and leaves original unchanged`() {
    val first = Payload("first")
    val middle = Payload("middle")
    val third = Payload("third")
    val map = VersionedPayloadMap.create(10, first, 30, third)

    val updated = map.insert(20, middle)!!
    assertNotSame(map, updated)

    assertEquals(2, map.size())
    assertSame(first, map.lowerBound(29))
    assertSame(third, map.lowerBound(30))

    assertEquals(3, updated.size())
    assertSame(first, updated.lowerBound(19))
    assertSame(middle, updated.lowerBound(20))
    assertSame(middle, updated.lowerBound(29))
    assertSame(third, updated.lowerBound(30))
  }

  @Test
  fun `cleanup removes stale versions but keeps closest predecessor needed for lower bound`() {
    val first = Payload("first")
    val second = Payload("second")
    val third = Payload("third")
    val latest = Payload("latest")
    val map = VersionedPayloadMap.create(10, first, 20, second)
      .insert(30, third)!!
      .insert(50, latest)!!

    val cleaned = map.cleanupStaleVersions(35)!!
    assertEquals(2, cleaned.size())
    assertNull(cleaned.lowerBound(29))
    assertSame(third, cleaned.lowerBound(35))
    assertSame(third, cleaned.lowerBound(49))
    assertSame(latest, cleaned.lowerBound(50))
    assertSame(latest, cleaned.lowerBound(60))
  }

  @Test
  fun `cleanup returns new map and leaves original unchanged`() {
    val first = Payload("first")
    val second = Payload("second")
    val third = Payload("third")
    val latest = Payload("latest")
    val map = VersionedPayloadMap.create(10, first, 20, second)
      .insert(30, third)!!
      .insert(50, latest)!!

    val cleaned = map.cleanupStaleVersions(35)!!
    assertNotSame(map, cleaned)

    assertEquals(4, map.size())
    assertSame(second, map.lowerBound(29))
    assertSame(third, map.lowerBound(35))
    assertSame(latest, map.lowerBound(50))

    assertEquals(2, cleaned.size())
    assertSame(third, cleaned.lowerBound(35))
    assertSame(latest, cleaned.lowerBound(50))
  }

  @Test
  fun `cleanup returns null when it cannot remove any version`() {
    val first = Payload("first")
    val second = Payload("second")
    val map = VersionedPayloadMap.create(10, first, 20, second)

    assertNull(map.cleanupStaleVersions(15))
  }

  @Test
  fun `cleanup of two-entry map keeps only closest predecessor when both versions are stale`() {
    val first = Payload("first")
    val second = Payload("second")
    val map = VersionedPayloadMap.create(10, first, 20, second)

    val cleaned = map.cleanupStaleVersions(20)!!

    assertEquals("VersionedPayloadMap1", cleaned.javaClass.simpleName)
    assertEquals(1, cleaned.size())
    assertNull(cleaned.lowerBound(19))
    assertSame(second, cleaned.lowerBound(20))
    assertSame(second, cleaned.lowerBound(30))
  }

  private class Payload(private val name: String) {
    override fun toString(): String {
      return name
    }
  }
}
