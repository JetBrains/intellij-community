// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Checks the atomic bulk mutation of [SettingsContext.mutate] and [SettingsMutationBuilder]. One
 * [SettingsMutationBuilder.apply] applies all the entries of a batch together.
 *
 * The tests use one in-memory [TestLayerProvider] that accepts every context. A read after a completed apply sees its
 * values, so a test reads them back without a poll.
 */
@TestApplication
class SettingsBulkMutationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val project get() = projectFixture.get()

  @BeforeEach
  fun mask() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
  }

  @Test
  fun `several entries commit together`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val a = SettingsKey("test.bulk.a", default = "da")
    val b = SettingsKey("test.bulk.b", default = "db")

    app.mutate().set(a, "A").set(b, "B").apply()

    assertEquals("A", a[app])
    assertEquals("B", b[app])
  }

  @Test
  fun `a listener of one entry sees the other entries of the batch`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val a = SettingsKey("test.bulk.seen.a", default = "da")
    val b = SettingsKey("test.bulk.seen.b", default = "db")
    val bSeenByA = CopyOnWriteArrayList<String>()
    val aSeenByB = CopyOnWriteArrayList<String>()
    a.addListener(disposable, app) { _, ctx -> bSeenByA.add(b[ctx]) }
    b.addListener(disposable, app) { _, ctx -> aSeenByB.add(a[ctx]) }

    app.mutate().set(a, "A").set(b, "B").apply()

    awaitEquals(listOf("B")) { bSeenByA.toList() }
    awaitEquals(listOf("A")) { aSeenByB.toList() }
  }

  @Test
  fun `a reader in the middle of a batch sees none of its entries`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val a = SettingsKey("test.bulk.middle.a", default = "da")
    val b = SettingsKey("test.bulk.middle.b", default = "db")
    val seen = CopyOnWriteArrayList<Pair<String, String>>()
    provider.onStorageContext = { seen.add(a[app] to b[app]) }

    try {
      app.mutate().set(a, "A").set(b, "B").apply()
    }
    finally {
      provider.onStorageContext = null
    }

    assertTrue(seen.isNotEmpty(), "the vault asked for the storage context while it applied the batch")
    assertEquals(setOf("da" to "db"), seen.toSet(), "a read during the batch saw a part of it")
    assertEquals("A", a[app])
    assertEquals("B", b[app])
  }

  @Test
  fun `a listener of a separate write sees the other key before it is written`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val a = SettingsKey("test.bulk.separate.a", default = "da")
    val b = SettingsKey("test.bulk.separate.b", default = "db")
    val bSeenByA = CopyOnWriteArrayList<String>()
    a.addListener(disposable, app) { _, ctx -> bSeenByA.add(b[ctx]) }

    a[app] = "A"
    b[app] = "B"

    awaitEquals(listOf("db")) { bSeenByA.toList() }
  }

  @Test
  fun `a per-entry context override lands the entry at another layer`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val projectCtx = SettingsContext.project(project)
    val key = SettingsKey("test.bulk.scoped", default = "d")

    projectCtx.mutate()
      .set(key, "project")
      .set(key, "app", context = app)
      .apply()

    assertEquals("app", key[app])
    assertEquals("project", key[projectCtx])  // the project layer shadows the app one
  }

  @Test
  fun `context switches the default for the entries that follow`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val projectCtx = SettingsContext.project(project)
    val key = SettingsKey("test.bulk.switch", default = "d")

    app.mutate()
      .set(key, "app")            // initial context (app)
      .context(projectCtx)
      .set(key, "project")        // after the switch
      .apply()

    assertEquals("app", key[app])
    assertEquals("project", key[projectCtx])
  }

  @Test
  fun `a slotted entry lands in the slot its context pins, not the one the batch selects`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val current = SettingsKey("test.bulk.current", default = "a")
    val data = SettingsMapKey("test.bulk.data", discriminatorKey = current)
    val child = SettingsKey("test.bulk.child", default = "none", storageKey = data)

    // In one batch: switch the discriminator, then write the child against the slot its own context pins. The
    // discriminator the batch just set does not change where the child lands.
    app.mutate().set(current, "b").set(child, "x", app.access { this[current] = "a" }).apply()

    assertEquals("x", child[app.access { this[current] = "a" }])      // the pinned slot holds it
    assertEquals("none", child[app.access { this[current] = "b" }])   // the newly current slot is untouched
    assertEquals("none", child[app])                                  // current is now "b" → slot "b"
  }

  @Test
  fun `a batch rejects a slotted entry with no pinned slot`() {
    val app = SettingsContext.app()
    val current = SettingsKey("test.bulk.unpinned.current", default = "a")
    val data = SettingsMapKey("test.bulk.unpinned.data", discriminatorKey = current)
    val child = SettingsKey("test.bulk.unpinned.child", default = "none", storageKey = data)
    val plain = SettingsKey("test.bulk.unpinned.plain", default = "d")

    assertThrows<UnpinnedDiscriminatorException> {
      runBlocking { app.mutate().set(plain, "written").set(child, "x").apply() }
    }

    assertEquals("d", plain[app])  // the batch is rejected up front, so the first entry never applied
  }

  @Test
  fun `a rejected entry fails the whole batch and writes nothing`() {
    val app = SettingsContext.app()
    val ok = SettingsKey("test.bulk.ok", default = "d")
    val readonly = SettingsKey("test.bulk.ro", default = "d", readonly = true)

    assertThrows<ReadOnlySettingsException> {
      runBlocking { app.mutate().set(ok, "written").set(readonly, "x").apply() }
    }

    assertEquals("d", ok[app])  // the whole batch was rejected up front, so the first entry never applied
  }

  @Test
  fun `all operation kinds in one transaction all land`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val projectCtx = SettingsContext.project(project)
    val pa = SettingsKey("test.tx.pa", default = "dpa")
    val pb = SettingsKey("test.tx.pb", default = "dpb")
    val disc = SettingsKey("test.tx.disc", default = "d0")
    val data = SettingsMapKey("test.tx.data", discriminatorKey = disc)
    val child = SettingsKey("test.tx.child", default = "none", storageKey = data)

    app.mutate()
      .set(pa, "A")
      .set(pb, "B")
      .set(disc, "s1")
      .set(child, "c1", context = app.access { this[disc] = "s1" })  // the slot this entry pins
      .copySlot(data, from = "s1", to = "s2")                    // s2 := copy of s1
      .set(child, "c3", context = app.access { this[disc] = "s3" })  // per-entry pinned slot s3
      .dropSlot(data, "s3")                                      // remove s3
      .renameSlot(data, from = "s2", to = "s2r")                 // s2 -> s2r
      .context(projectCtx).set(pa, "Ap")                         // switch layer for the rest
      .apply()

    assertEquals("A", pa[app])
    assertEquals("B", pb[app])
    assertEquals("s1", disc[app])
    assertEquals("c1", child[app.accessFor(child)])                   // current disc s1
    assertEquals("c1", child[app.access { this[disc] = "s1" }])
    assertEquals("c1", child[app.access { this[disc] = "s2r" }])      // copied from s1, then renamed
    assertEquals("none", child[app.access { this[disc] = "s2" }])     // renamed away
    assertEquals("none", child[app.access { this[disc] = "s3" }])     // created then dropped
    assertEquals("Ap", pa[projectCtx])                                // landed at the switched (project) layer
    assertEquals("B", pb[projectCtx])                                 // inherited from app
  }

  /**
   * Prints how the cost of one write grows with the size of the layer that it writes into. It asserts no timing.
   *
   * Each write copies the layer map that it changes, because the model is immutable (ADR 0136). So the time of one
   * write should grow about linearly with the layer size. That growth is the signal for a map with structural sharing.
   * Read the shape of the curve and the absolute cost at small, realistic sizes, not one number.
   */
  @Test
  @Disabled("Local-only: for hand-evaluating mutation performance while changing the mutation logic. Not for CI — " +
            "a CI perf check needs proper metric reporting, not this print-based measurement.")
  fun `write cost vs layer size`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val w = SettingsKey("test.perf.w", default = "d")
    val writes = 100_000

    // Fill the layer with writes, not with `provider.seed`. A seed is a push, and the vault ignores a push while a save
    // of that store is pending. The write loop below leaves the save pending, so the next seed would be lost. The fill
    // adds to the earlier fill, so each measured write copies a layer of that size.
    var populated = 0
    for (size in listOf(0, 10, 100, 1_000, 10_000)) {
      if (size > populated) {
        val fill = app.mutate()
        (populated until size).forEach { fill.set(SettingsKey("test.perf.seed.$it", default = "d"), "v") }
        fill.apply()
        populated = size
      }
      val ns = measureNanoTime { repeat(writes) { i -> w[app] = "v$i" } }
      println("[SettingsBulkMutationTest] layer %6d keys: %.2f µs/write".format(size, ns.toDouble() / writes / 1_000))
      assertEquals("v${writes - 1}", w[app])
    }
  }

  @Test
  fun `transactional discriminator plus dependent value stays consistent under concurrent discriminator churn`(): Unit = runBlocking {
    val app = SettingsContext.app()
    val disc = SettingsKey("test.torture.disc", default = "d0")
    val data = SettingsMapKey("test.torture.data", discriminatorKey = disc)
    val child = SettingsKey("test.torture.child", default = "none", storageKey = data)
    val iterations = 300

    coroutineScope {
      // Several coroutines write the shared discriminator with no pinned slot. Each write takes the key set operator
      // or the builder at random, so both write paths compete.
      val chaos = List(4) { worker ->
        launch {
          var n = 0
          while (isActive) {
            val value = "chaos-$worker-${n++}"
            if (Random.nextBoolean()) disc[app] = value
            else app.mutate().set(disc, value).apply()
          }
        }
      }
      // Each iteration sets the discriminator to a new slot and writes the child at that pinned slot in one batch. A
      // chaos write cannot change where the child lands, so the value lands in the slot of that iteration every time.
      try {
        for (i in 0 until iterations) {
          val slot = "slot-$i"
          app.mutate().set(disc, slot).set(child, "val-$i", app.access { this[disc] = slot }).apply()
          assertEquals("val-$i", child[app.access { this[disc] = slot }], "iteration $i")
        }
      }
      finally {
        chaos.forEach { it.cancel() }
      }
    }
  }
}
