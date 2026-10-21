// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.util.containers.Java11Shim
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrentLongLongHashMapTest {
  @Test
  fun `stores full width keys and values`() {
    val map = Java11Shim.createConcurrentLongLongMap(-1, 100)
    val keys = longArrayOf(0, 1, 1L + (1L shl 32), Long.MIN_VALUE, Long.MAX_VALUE)
    val values = longArrayOf(0, Long.MIN_VALUE, Long.MAX_VALUE, 1L shl 40, -(1L shl 40))

    assertThat(map.isEmpty()).isTrue()
    for (index in keys.indices) {
      assertThat(map.put(keys[index], values[index])).isEqualTo(-1)
    }
    assertThat(map.size()).isEqualTo(keys.size)
    assertThat(map.mappingCount()).isEqualTo(keys.size.toLong())
    for (index in keys.indices) {
      assertThat(map[keys[index]]).isEqualTo(values[index])
      assertThat(map.containsKey(keys[index])).isTrue()
      assertThat(map.containsValue(values[index])).isTrue()
    }
    assertThat(map.keys()).containsExactlyInAnyOrder(*keys)
    assertThat(map.entrySet().map { it.key to it.value }).containsExactlyInAnyOrderElementsOf(keys.zip(values))
    assertThat(map.getOrDefault(2, 1L shl 50)).isEqualTo(1L shl 50)
    assertThatIllegalArgumentException().isThrownBy { Java11Shim.createConcurrentLongLongMap(0, -1) }
  }

  @Test
  fun `resizes and handles colliding keys`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongLongMap(-1)
    for (index in 0L..1000L) {
      val key = (index shl 32) or index
      assertThat(map.put(key, Long.MAX_VALUE - index)).isEqualTo(-1)
    }
    for (index in 0L..1000L) {
      val key = (index shl 32) or index
      assertThat(map[key]).isEqualTo(Long.MAX_VALUE - index)
      assertThat(map.remove(key)).isEqualTo(Long.MAX_VALUE - index)
    }
    assertThat(map.isEmpty()).isTrue()
  }

  @Test
  fun `updates preserve full width values`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongLongMap(-1)
    val value = 1L shl 40

    assertThat(map.putIfAbsent(1, value)).isEqualTo(-1)
    assertThat(map.putIfAbsent(1, value + 1)).isEqualTo(value)
    assertThat(map.replace(1, value + 1, value + 2)).isFalse()
    assertThat(map.replace(1, value, value + 2)).isTrue()
    assertThat(map.replace(1, value)).isEqualTo(value + 2)
    assertThat(map.computeIfAbsent(2) { value + it }).isEqualTo(value + 2)
    assertThat(map.computeIfAbsent(2) { error("The key is present") }).isEqualTo(value + 2)
    assertThat(map.computeIfPresent(2) { key, old -> old + key }).isEqualTo(value + 4)
    assertThat(map.compute(3) { key, old ->
      assertThat(old).isEqualTo(-1)
      value + key
    }).isEqualTo(value + 3)
    assertThat(map.merge(3, value) { first, second -> first + second }).isEqualTo(2 * value + 3)
    map.replaceAll { key, old -> old + key }
    val entries = HashMap<Long, Long>()
    map.forEach { key, current -> entries[key] = current }
    assertThat(entries).containsExactlyInAnyOrderEntriesOf(mapOf(1L to value + 1, 2L to value + 6, 3L to 2 * value + 6))
    assertThat(map.hashCode()).isEqualTo(entries.hashCode())
    assertThat(map.entrySet().hashCode()).isEqualTo(entries.entries.hashCode())
    assertThat(map.remove(1, value)).isFalse()
    assertThat(map.remove(1, value + 1)).isTrue()
    map.clear()
    assertThat(map.keys()).isEmpty()
    assertThat(map.entrySet()).isEmpty()
  }

  @Test
  fun `default values remove mappings`() {
    val defaultValue = Long.MIN_VALUE
    val map = ConcurrentCollectionFactory.createConcurrentLongLongMap(defaultValue)
    val value = Long.MAX_VALUE

    assertThat(map.put(1, defaultValue)).isEqualTo(defaultValue)
    assertThat(map.containsKey(1)).isFalse()
    map.put(1, value)
    assertThat(map.put(1, defaultValue)).isEqualTo(value)
    assertThat(map[1]).isEqualTo(defaultValue)
    map.put(1, value)
    assertThat(map.computeIfPresent(1) { _, _ -> defaultValue }).isEqualTo(defaultValue)
    map.put(1, value)
    assertThat(map.compute(1) { _, _ -> defaultValue }).isEqualTo(defaultValue)
    map.put(1, value)
    assertThat(map.merge(1, value) { _, _ -> defaultValue }).isEqualTo(defaultValue)
    assertThat(map.computeIfAbsent(1) { defaultValue }).isEqualTo(defaultValue)
    assertThat(map.size()).isZero()
    assertThat(map.mappingCount()).isZero()
    assertThat(map.isEmpty()).isTrue()
  }

  @Test
  fun `concurrent updates preserve mappings during resize`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongLongMap(-1)
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(4)
    map.put(Long.MIN_VALUE, 0)
    try {
      val tasks = (0L..3L).map { worker ->
        executor.submit {
          assertThat(start.await(10, TimeUnit.SECONDS)).isTrue()
          for (index in 0L until 1000L) {
            val key = (worker shl 32) or index
            val value = Long.MAX_VALUE - key
            assertThat(map.putIfAbsent(key, value)).isEqualTo(-1)
            assertThat(map[key]).isEqualTo(value)
            map.compute(Long.MIN_VALUE) { _, old -> old + 1 }
          }
        }
      }
      start.countDown()
      tasks.forEach { it.get(30, TimeUnit.SECONDS) }
      assertThat(map[Long.MIN_VALUE]).isEqualTo(4000)
      assertThat(map.size()).isEqualTo(4001)
      for (worker in 0L..3L) {
        for (index in 0L until 1000L) {
          val key = (worker shl 32) or index
          assertThat(map[key]).isEqualTo(Long.MAX_VALUE - key)
        }
      }
    }
    finally {
      executor.shutdownNow()
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
    }
  }
}
