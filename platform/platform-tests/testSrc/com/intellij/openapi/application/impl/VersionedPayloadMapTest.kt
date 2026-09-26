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

  /** AI-generated test. */
  @Test
  fun `specialized maps skip exclusive modification versions as lower bound predecessors`() {
    val published = Payload("published")
    val exclusive = Payload("exclusive")

    val oneEntryMap = VersionedPayloadMap.empty().insert(11, exclusive)!!
    assertSame(exclusive, oneEntryMap.lowerBound(11))
    assertNull(oneEntryMap.lowerBound(12))

    val twoEntryMap = oneEntryMap.insert(20, published)!!
    assertSame(exclusive, twoEntryMap.lowerBound(11))
    assertNull(twoEntryMap.lowerBound(19))
    assertSame(published, twoEntryMap.lowerBound(20))

    val latestExclusiveMap = VersionedPayloadMap.create(10, published, 11, exclusive)
    assertSame(exclusive, latestExclusiveMap.lowerBound(11))
    assertSame(published, latestExclusiveMap.lowerBound(12))
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

    val cleaned = map.cleanupStaleVersions(36)!!
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

    val cleaned = map.cleanupStaleVersions(36)!!
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

    assertNull(map.cleanupStaleVersions(16))
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

  /** AI-generated test. */
  @Test
  fun `cleanup of a two-entry map collapses onto a forked timeline entry`() {
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.create(10, published, 11, exclusive)

    // `11` is visible as a lower bound of `11` only, hence it cannot stand in for `10`: nothing may be removed here
    assertNull(map.cleanupStaleVersions(10)) // the forked timeline is live, so its base must survive
    assertEquals(1, map.cleanupStaleVersions(12)?.size()) // the forked timeline is dead, but `10` is still the lower bound

    assertSame(exclusive, map.lowerBound(11))
    assertSame(published, map.lowerBound(12))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of a multi entry map retains the newest main timeline version`() {
    val stale = Payload("stale")
    val staleExclusive = Payload("staleExclusive")
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.create(10, stale, 11, staleExclusive)
      .insert(20, published)!!
      .insert(21, exclusive)!!

    val cleaned = map.cleanupStaleVersions(22)!!

    assertEquals(1, cleaned.size())
    assertSame(published, cleaned.lowerBound(20))
    assertSame(published, cleaned.lowerBound(21))
    assertSame(published, cleaned.lowerBound(22))
    assertSame(map.lowerBound(22), cleaned.lowerBound(22))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup keeps a forked entry that is newer than the barrier`() {
    val stale = Payload("stale")
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.create(10, stale, 20, published)
      .insert(21, exclusive)!!

    // `20` is the base of the live forked timeline `21`
    val cleaned = map.cleanupStaleVersions(20)!!

    assertEquals(2, cleaned.size())
    assertSame(exclusive, cleaned.lowerBound(21))
    assertSame(published, cleaned.lowerBound(22))
    assertSame(map.lowerBound(22), cleaned.lowerBound(22))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup collects forked versions once the main timeline advances past them`() {
    val stale = Payload("stale")
    val staleExclusive = Payload("staleExclusive")
    val published = Payload("published")
    val map = VersionedPayloadMap.create(10, stale, 11, staleExclusive)
      .insert(20, published)!!

    val cleaned = map.cleanupStaleVersions(30)!!

    assertEquals("VersionedPayloadMap1", cleaned.javaClass.simpleName)
    assertEquals(1, cleaned.size())
    assertNull(cleaned.lowerBound(11))
    assertSame(published, cleaned.lowerBound(30))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of an array map keeps a forked entry above the barrier`() {
    val stale = Payload("stale")
    val staleExclusive = Payload("staleExclusive")
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.create(10, stale, 11, staleExclusive)
      .insert(20, published)!!
      .insert(21, exclusive)!!

    // `20` is the base of the live forked timeline `21`
    val cleaned = map.cleanupStaleVersions(20)!!

    assertEquals(2, cleaned.size())
    assertSame(published, cleaned.lowerBound(20))
    assertSame(exclusive, cleaned.lowerBound(21))
    assertSame(published, cleaned.lowerBound(22))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of an array map removes nothing when the barrier is the oldest entry`() {
    val stale = Payload("stale")
    val staleExclusive = Payload("staleExclusive")
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.create(10, stale, 11, staleExclusive)
      .insert(20, published)!!
      .insert(21, exclusive)!!

    assertNull(map.cleanupStaleVersions(10))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of a one-entry map collects a dead forked entry`() {
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.empty().insert(11, exclusive)!!

    // the main timeline advanced past `11`, so no live version can observe it
    val cleaned = map.cleanupStaleVersions(12)!!

    assertEquals(0, cleaned.size())
    assertNull(cleaned.lowerBound(11))
    assertNull(cleaned.lowerBound(12))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of a one-entry map keeps a live forked entry`() {
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.empty().insert(11, exclusive)!!

    // `10` is the base of the live forked timeline `11`
    assertNull(map.cleanupStaleVersions(10))
    assertSame(exclusive, map.lowerBound(11))
  }


  /** AI-generated test. */
  @Test
  fun `cleanup of a one-entry map keeps a main timeline entry`() {
    val published = Payload("published")
    val map = VersionedPayloadMap.empty().insert(10, published)!!

    assertNull(map.cleanupStaleVersions(20))
    assertSame(published, map.lowerBound(20))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of a two-entry map keeps a forked entry that the barrier reaches`() {
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    // `13` forks `12`, and a client frozen at `12` holds the barrier there
    val map = VersionedPayloadMap.create(10, published, 13, exclusive)

    val cleaned = map.cleanupStaleVersions(12) ?: map

    assertSame(published, cleaned.lowerBound(12))
    assertSame(exclusive, cleaned.lowerBound(13)) // `13` is reachable from `12`, so the forked timeline is live
    assertSame(published, cleaned.lowerBound(14))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of an array map keeps a forked entry that the barrier reaches`() {
    val ancient = Payload("ancient")
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val latest = Payload("latest")
    // `13` forks `12`, and a client frozen at `12` holds the barrier there
    val map = VersionedPayloadMap.create(8, ancient, 10, published)
      .insert(13, exclusive)!!
      .insert(20, latest)!!

    val cleaned = map.cleanupStaleVersions(12) ?: map

    assertSame(published, cleaned.lowerBound(12))
    assertSame(exclusive, cleaned.lowerBound(13)) // `13` is reachable from `12`, so the forked timeline is live
    assertSame(published, cleaned.lowerBound(14))
    assertSame(latest, cleaned.lowerBound(20))
  }

  /** AI-generated test. */
  @Test
  fun `cleanup of an array map keeps the entries above a collected forked entry`() {
    val ancient = Payload("ancient")
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val latest = Payload("latest")
    val map = VersionedPayloadMap.create(8, ancient, 10, published)
      .insert(11, exclusive)!!
      .insert(20, latest)!!

    val cleaned = map.cleanupStaleVersions(12) ?: map

    assertSame(published, cleaned.lowerBound(12))
    assertSame(published, cleaned.lowerBound(19))
    assertSame(latest, cleaned.lowerBound(20))
    assertSame(latest, cleaned.lowerBound(30))
  }

  /** AI-generated test. */
  @Test
  fun `test many exclusive versions`() {
    val ancient = Payload("ancient")
    val published = Payload("published")
    val exclusive = Payload("exclusive")
    val map = VersionedPayloadMap.create(8, ancient, 10, published)
      .insert(11, exclusive)!!
      .insert(13, exclusive)!!
      .insert(15, exclusive)!!
      .insert(17, exclusive)!!

    val cleaned = map.cleanupStaleVersions(16) ?: map

    assertEquals(2, cleaned.size())
    assertNull(cleaned.lowerBound(9))
    for (i in 10L..16) {
      assertSame(published, cleaned.lowerBound(i))
    }
    assertSame(exclusive, cleaned.lowerBound(17))
  }

  /**
   * AI-generated test.
   *
   * The contract of [VersionedPayloadMap.cleanupStaleVersions] over every flavor of the map.
   *
   * The barrier belongs to the main timeline, so every version that a client can still hold is at or above it.
   * Cleanup must not change what such a version observes.
   *
   * This sweep uses even barriers only, so it cannot find a defect that needs an odd barrier.
   * It does find a wrong retention boundary.
   */
  @Test
  fun `cleanup preserves the lower bound of every version above the barrier`() {
    for (map in mapsUnderTest()) {
      for (barrier in 8..32 step 2) {
        val cleaned = map.cleanupStaleVersions(barrier.toLong()) ?: continue
        assertTrue(cleaned.size() <= map.size(), "$map grew after a cleanup with the barrier $barrier")
        for (version in barrier..40) {
          assertSame(
            map.lowerBound(version.toLong()),
            cleaned.lowerBound(version.toLong()),
            "$map lost the lower bound of $version after a cleanup with the barrier $barrier",
          )
        }
      }
    }
  }

  /**
   * AI-generated test.
   *
   * A map that holds forked entries only appears when a fork is the sole writer of a field. Once the main timeline
   * passes each of those versions, no live version can observe any entry, so the whole map is garbage.
   *
   * [VersionedPayloadMap] with one entry collects such an entry. This test states the same for the array flavor.
   */
  @Test
  fun `cleanup of an array map of forked entries only collects every entry`() {
    val first = Payload("first")
    val second = Payload("second")
    val third = Payload("third")
    val map = VersionedPayloadMap.create(11, first, 13, second).insert(15, third)!!

    val cleaned = map.cleanupStaleVersions(20) ?: map

    assertEquals(0, cleaned.size(), "no live version can observe $map with the barrier 20")
    assertNull(cleaned.lowerBound(20))
    assertNull(cleaned.lowerBound(21))
  }

  /**
   * AI-generated test.
   *
   * The two-entry flavor of the same claim as
   * `cleanup of an array map of forked entries only collects every entry`.
   */
  @Test
  fun `cleanup of a two-entry map of forked entries only collects every entry`() {
    val first = Payload("first")
    val second = Payload("second")
    val map = VersionedPayloadMap.create(11, first, 13, second)

    val cleaned = map.cleanupStaleVersions(20) ?: map

    assertEquals(0, cleaned.size(), "no live version can observe $map with the barrier 20")
    assertNull(cleaned.lowerBound(20))
    assertNull(cleaned.lowerBound(21))
  }

  /**
   * AI-generated test.
   *
   * The counterpart of `cleanup preserves the lower bound of every version above the barrier`.
   *
   * That test states what a cleanup must keep. This one states what a cleanup must drop: a forked entry below the
   * barrier is observable by its own version only, and the barrier has passed that version, so the entry is garbage.
   */
  @Test
  fun `cleanup collects every entry that no live version can observe`() {
    for (map in mapsUnderTest()) {
      for (barrier in 8..32 step 2) {
        val effective = map.cleanupStaleVersions(barrier.toLong()) ?: map
        val garbage = effective.arrayOfPairs().filter { (version, _) -> version % 2 != 0L && version < barrier }
        assertTrue(
          garbage.isEmpty(),
          "$map kept $garbage after a cleanup with the barrier $barrier",
        )
      }
    }
  }

  /**
   * AI-generated test.
   *
   * A cleanup with the same barrier twice must find nothing to do the second time. The garbage collector runs on every
   * change of the barrier, and it can observe the same barrier more than once, so a repeated call must build no new map.
   *
   * The empty flavor returns itself in place of `null`, so this test accepts the same instance as "nothing to do".
   */
  @Test
  fun `cleanup with the same barrier finds nothing to do the second time`() {
    for (map in mapsUnderTest()) {
      for (barrier in 8..32 step 2) {
        val cleaned = map.cleanupStaleVersions(barrier.toLong()) ?: continue
        val again = cleaned.cleanupStaleVersions(barrier.toLong())
        assertTrue(
          again == null || again === cleaned,
          "$map still had work for the barrier $barrier after one cleanup: $again",
        )
      }
    }
  }

  /**
   * AI-generated test.
   *
   * `InternalPsiVersioning.getCreationPsiVersionForElement` returns `-1` for a non-versioned element, so `-1` can
   * reach the map. It is not a forked version, so every later version must reach it.
   */
  @Test
  fun `the non-versioned marker reaches every later version`() {
    val nonVersioned = Payload("nonVersioned")
    val published = Payload("published")

    val oneEntryMap = VersionedPayloadMap.empty().insert(-1, nonVersioned)!!
    assertSame(nonVersioned, oneEntryMap.lowerBound(-1))
    assertSame(nonVersioned, oneEntryMap.lowerBound(0))
    assertSame(nonVersioned, oneEntryMap.lowerBound(11))

    val twoEntryMap = oneEntryMap.insert(10, published)!!
    assertSame(nonVersioned, twoEntryMap.lowerBound(-1))
    assertSame(nonVersioned, twoEntryMap.lowerBound(9))
    assertSame(published, twoEntryMap.lowerBound(10))
    assertSame(published, twoEntryMap.lowerBound(11))
  }

  /**
   * One map per flavor. The odd versions belong to forked timelines.
   */
  private fun mapsUnderTest(): List<VersionedPayloadMap> {
    val a = Payload("a")
    val b = Payload("b")
    val c = Payload("c")
    val d = Payload("d")
    return listOf(
      VersionedPayloadMap.empty().insert(10, a)!!,
      VersionedPayloadMap.empty().insert(11, a)!!,
      VersionedPayloadMap.create(10, a, 20, b),
      VersionedPayloadMap.create(10, a, 11, b),
      VersionedPayloadMap.create(10, a, 11, b).insert(20, c)!!.insert(21, d)!!,
      VersionedPayloadMap.create(10, a, 11, b).insert(20, c)!!.insert(30, d)!!,
      VersionedPayloadMap.create(10, a, 20, null).insert(30, c)!!,
      // a forked timeline that the barrier can still reach
      VersionedPayloadMap.create(10, a, 13, b),
      // a forked entry between two entries of the main timeline
      VersionedPayloadMap.create(8, a, 10, b).insert(11, c)!!.insert(20, d)!!,
      // the same shape, but the barrier can still reach the forked entry
      VersionedPayloadMap.create(8, a, 10, b).insert(13, c)!!.insert(20, d)!!,
      // several forked timelines over one entry of the main timeline
      VersionedPayloadMap.create(8, a, 10, b).insert(11, c)!!.insert(13, d)!!.insert(15, c)!!.insert(17, d)!!,
      // forked entries only, so a barrier above each of them makes the whole map garbage
      VersionedPayloadMap.create(11, a, 13, b),
      VersionedPayloadMap.create(11, a, 13, b).insert(15, c)!!,
    )
  }

  private class Payload(private val name: String) {
    override fun toString(): String {
      return name
    }
  }
}
