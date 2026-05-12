// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Ref
import com.intellij.psi.impl.source.tree.mvcc.ConcurrentWeakVersionedValueHashMap
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.junit.jupiter.api.Test
import java.util.function.BiConsumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
internal class ConcurrentWeakVersionedValueHashMapTest {

  @Test
  fun `removed live mapping stays reachable while psi version is frozen`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val map = ConcurrentWeakVersionedValueHashMap<String, Payload>()
    val initialPayload = Payload("initial")

    runWriteAction {
      assertNull(map.put(KEY, initialPayload))
    }

    PsiVersioningService.freezePsiVersion {
      assertSingleKeyMapping(map, initialPayload)

      async(Dispatchers.Default) {
        backgroundWriteAction {
          assertSame(initialPayload, map.remove(KEY))
        }
      }.asCompletableFuture().get()

      assertSingleKeyMapping(map, initialPayload)
    }

    assertNoKeyMapping(map, initialPayload)
  }

  @Test
  fun `reinserted key keeps old live value for frozen psi version`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val map = ConcurrentWeakVersionedValueHashMap<String, Payload>()
    val initialPayload = Payload("initial")
    val replacementPayload = Payload("replacement")

    runWriteAction {
      assertNull(map.put(KEY, initialPayload))
    }

    PsiVersioningService.freezePsiVersion {
      async(Dispatchers.Default) {
        backgroundWriteAction {
          assertSame(initialPayload, map.remove(KEY))
        }
      }.asCompletableFuture().get()

      assertSingleKeyMapping(map, initialPayload)

      async(Dispatchers.Default) {
        backgroundWriteAction {
          assertNull(map.putIfAbsent(KEY, replacementPayload))
        }
      }.asCompletableFuture().get()

      assertSingleKeyMapping(map, initialPayload)
    }

    assertSingleKeyMapping(map, replacementPayload)
  }

  @Test
  fun `clear keeps live mappings reachable while psi version is frozen`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val map = ConcurrentWeakVersionedValueHashMap<String, Payload>()
    val firstPayload = Payload("first")
    val secondPayload = Payload("second")

    runWriteAction {
      map[FIRST_KEY] = firstPayload
      map[SECOND_KEY] = secondPayload
    }

    PsiVersioningService.freezePsiVersion {
      async(Dispatchers.Default) {
        backgroundWriteAction {
          map.clear()
        }
      }.asCompletableFuture().get()

      assertEquals(mapOf(FIRST_KEY to firstPayload, SECOND_KEY to secondPayload), map.snapshot())
      assertEquals(setOf(FIRST_KEY, SECOND_KEY), map.keys)
      assertEquals(2, map.size)
      assertFalse(map.isEmpty())
    }

    assertTrue(map.isEmpty())
    assertTrue(map.keys.isEmpty())
    assertTrue(map.values.isEmpty())
    assertTrue(map.entries.isEmpty())
  }

  @Test
  fun `collected current value disappears from visible views`() {
    val map = ConcurrentWeakVersionedValueHashMap<String, Payload>()
    val watcher = storeWeakValue(map)

    watcher.ensureCollected()

    assertNoKeyMapping(map)
  }

  @Test
  fun `put if absent stores new value when previous value was collected`() {
    val map = ConcurrentWeakVersionedValueHashMap<String, Payload>()
    val watcher = storeWeakValue(map)

    watcher.ensureCollected()

    val replacementPayload = Payload("replacement")

    assertNull(map.putIfAbsent(KEY, replacementPayload))
    assertSingleKeyMapping(map, replacementPayload)
  }

  @Test
  fun `concurrent map mutators use currently visible live values`() {
    val map = ConcurrentWeakVersionedValueHashMap<String, Payload>()
    val one = Payload("one")
    val two = Payload("two")
    val three = Payload("three")
    val four = Payload("four")
    val five = Payload("five")
    val six = Payload("six")
    val seven = Payload("seven")
    val merged = Payload("merged")
    val putAllValue = Payload("putAll")
    val replacementB = Payload("replacementB")
    val replacementD = Payload("replacementD")

    assertNull(map.putIfAbsent("a", one))
    assertSame(one, map.putIfAbsent("a", two))
    assertFalse(map.remove("a", two))
    assertTrue(map.replace("a", one, two))
    assertSame(two, map.replace("a", three))
    assertSame(four, map.computeIfPresent("a") { _, value ->
      assertSame(three, value)
      four
    })
    assertNull(map.computeIfPresent("missing") { _, _ -> error("must not be called") })
    assertSame(five, map.computeIfAbsent("b") { five })
    assertNull(map.computeIfAbsent("missing") { null })
    assertFalse(map.containsKey("missing"))
    assertSame(six, map.compute("c") { _, value ->
      assertNull(value)
      six
    })
    assertSame(merged, map.merge("a", seven) { oldValue, newValue ->
      assertSame(four, oldValue)
      assertSame(seven, newValue)
      merged
    })
    assertNull(map.compute("a") { _, value ->
      assertSame(merged, value)
      null
    })
    assertFalse(map.containsKey("a"))

    map.putAll(mapOf("d" to putAllValue))
    val replacements = mapOf("b" to replacementB, "d" to replacementD)
    map.replaceAll { key, _ -> replacements[key] }

    assertEquals(replacements, map.snapshot())

    map.clear()
    assertTrue(map.isEmpty())
  }

  private fun installVersioningListeners(disposable: Disposable) {
    val listener = PsiVersioningLockingListener()
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addSuspendingWriteActionListener(listener, disposable)
  }

  private fun assertSingleKeyMapping(map: ConcurrentWeakVersionedValueHashMap<String, Payload>, value: Payload) {
    assertEquals(1, map.size)
    assertFalse(map.isEmpty())
    assertTrue(map.containsKey(KEY))
    assertTrue(map.containsValue(value))
    assertEquals(setOf(KEY), map.keys)
    assertEquals(listOf(value), map.values.toList())

    val entry = map.entries.single()
    assertEquals(KEY, entry.key)
    assertSame(value, entry.value)

    var forEachInvocations = 0
    map.forEach(BiConsumer { entryKey, entryValue ->
      assertEquals(KEY, entryKey)
      assertSame(value, entryValue)
      forEachInvocations++
    })
    assertEquals(1, forEachInvocations)
  }

  private fun assertNoKeyMapping(map: ConcurrentWeakVersionedValueHashMap<String, Payload>, value: Payload? = null) {
    assertNull(map[KEY])
    assertFalse(map.containsKey(KEY))
    if (value != null) {
      assertFalse(map.containsValue(value))
    }
    assertEquals(0, map.size)
    assertTrue(map.isEmpty())
    assertTrue(map.keys.isEmpty())
    assertTrue(map.values.isEmpty())
    assertTrue(map.entries.isEmpty())
  }

  private fun ConcurrentWeakVersionedValueHashMap<String, Payload>.snapshot(): Map<String, Payload> {
    return entries.associate { it.key to it.value }
  }

  private fun storeWeakValue(map: ConcurrentWeakVersionedValueHashMap<String, Payload>): GCWatcher {
    val ref = Ref.create(Payload("collected"))
    map[KEY] = ref.get()!!
    return GCWatcher.fromClearedRef(ref)
  }

  private class Payload(private val name: String) {
    override fun toString(): String {
      return name
    }
  }

  companion object {
    private const val KEY = "key"
    private const val FIRST_KEY = "first"
    private const val SECOND_KEY = "second"
  }
}
