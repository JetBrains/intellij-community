// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Checks the change listeners. A contextual listener fires only when the resolved value of its key at its context
 * really changes. A key-only listener tells that the key may have changed. A disposal removes a listener. A listener
 * of a slotted key fires on a change of the data of its slot and on a switch of its discriminator.
 *
 * No listener receives a value. The listener reads it.
 */
@TestApplication
class SettingsChangeListenerTest {
  companion object {
    private val plain = SettingsKey("test.listen.plain", default = "def")
    private val other = SettingsKey("test.listen.other", default = "def")
    private val langCurrent = SettingsKey("test.listen.lang.current", default = "kotlin")
    private val langData = SettingsMapKey("test.listen.lang.data", discriminatorKey = langCurrent)
    private val style = SettingsKey("test.listen.style", default = "none", storageKey = langData)

    // A one-level slotted storage (discriminator d1) holding two child keys.
    private val d1 = SettingsKey("test.listen.d1", default = "s1")
    private val m1 = SettingsMapKey("test.listen.m1", discriminatorKey = d1)
    private val k1a = SettingsKey("test.listen.k1a", default = "def", storageKey = m1)
    private val k1b = SettingsKey("test.listen.k1b", default = "def", storageKey = m1)

    // A two-level slotted storage (outer discriminator dOuter, inner dInner) holding two child keys.
    private val dOuter = SettingsKey("test.listen.dOuter", default = "o1")
    private val dInner = SettingsKey("test.listen.dInner", default = "i1")
    private val mOuter = SettingsMapKey("test.listen.mOuter", discriminatorKey = dOuter)
    private val mInner = SettingsMapKey("test.listen.mInner", discriminatorKey = dInner, storageKey = mOuter)
    private val k2a = SettingsKey("test.listen.k2a", default = "def", storageKey = mInner)
    private val k2b = SettingsKey("test.listen.k2b", default = "def", storageKey = mInner)

    private val childKeys = listOf(k1a, k1b, k2a, k2b)
    private val sentinel = SettingsKey("test.listen.sentinel", default = "def")
    private val burst = SettingsKey("test.listen.burst", default = "def")
    private val flip = SettingsKey("test.listen.flip", default = "def")
    private val consumerHolder = SettingsKey("test.listen.consumerHolder", default = "def")
    private val keyOnlyTarget = SettingsKey("test.listen.keyOnlyTarget", default = "def")
    private val defaultWrite = SettingsKey("test.listen.defaultWrite", default = "def")
    private val keyOnlyBurst = SettingsKey("test.listen.keyOnlyBurst", default = "def")

    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val app = SettingsContext.app()
  private val project get() = projectFixture.get()

  @BeforeEach
  fun mask() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
  }

  @Test
  fun `a contextual listener reads the new value on change, an unrelated key does not fire`(): Unit = runBlocking {
    val seen = AtomicReference<String?>(null)
    val otherFires = AtomicInteger(0)
    plain.addListener(disposable, app) { _, ctx -> seen.set(plain[ctx]) }
    other.addListener(disposable, app) { _, _ -> otherFires.incrementAndGet() }

    plain[app] = "one"
    awaitEquals("one") { seen.get() }
    plain[app] = "two"
    awaitEquals("two") { seen.get() }

    assertEquals(0, otherFires.get())  // a change to `plain` never touched the `other` watcher
  }

  @Test
  fun `a key-only listener is pinged on a change`(): Unit = runBlocking {
    val pings = AtomicInteger(0)
    plain.addListener(disposable) { _, _ -> pings.incrementAndGet() }

    plain[app] = "x"
    awaitEquals(true) { pings.get() > 0 }
  }

  @Test
  fun `a key-only listener carries the context where the change happened`(): Unit = runBlocking {
    val projectContext = SettingsContext.project(project)
    val changeContexts = CopyOnWriteArrayList<String>()
    keyOnlyTarget.addListener(disposable) { _, context -> changeContexts.add(context.identity) }

    keyOnlyTarget[projectContext] = "in-project"

    awaitEquals(listOf(projectContext.identity)) { changeContexts.toList() }
  }

  @Test
  fun `a write of the default fires a contextual listener when no layer held a value`(): Unit = runBlocking {
    val fires = AtomicInteger(0)
    defaultWrite.addListener(disposable, app) { _, _ -> fires.incrementAndGet() }

    defaultWrite[app] = "def"

    assertEquals(1, fires.get())  // a synchronous write delivers its listeners before returning
  }

  @Test
  fun `a key-only listener fires once per changed context in one model change`(): Unit = runBlocking {
    val projectContext = SettingsContext.project(project)
    val changeContexts = CopyOnWriteArrayList<String>()
    val sentinelFires = AtomicInteger(0)
    keyOnlyBurst.addListener(disposable) { _, context -> changeContexts.add(context.identity) }
    sentinel.addListener(disposable, app) { _, _ -> sentinelFires.incrementAndGet() }

    val release = holdPushConsumer()
    provider.seed(app, keyOnlyBurst, "app-team", LayerLocality.TEAM)
    provider.seed(app, keyOnlyBurst, "app-local", LayerLocality.LOCAL)
    provider.seed(projectContext, keyOnlyBurst, "project-team", LayerLocality.TEAM)
    provider.seed(projectContext, keyOnlyBurst, "project-local", LayerLocality.LOCAL)
    provider.seed(app, sentinel, "fenced")  // folds in the same burst, so its fire proves the burst was delivered
    release.countDown()

    awaitEquals(1) { sentinelFires.get() }
    assertEquals(listOf(app.identity, projectContext.identity).sorted(), changeContexts.sorted())
  }

  @Test
  fun `disposing the registration stops notifications`(): Unit = runBlocking {
    val seen = AtomicReference<String?>(null)
    val marker = AtomicInteger(0)
    val child = Disposer.newDisposable(disposable, "child")
    plain.addListener(child, app) { _, ctx -> seen.set(plain[ctx]) }
    other.addListener(disposable, app) { _, _ -> marker.incrementAndGet() }

    plain[app] = "one"
    awaitEquals("one") { seen.get() }

    Disposer.dispose(child)
    plain[app] = "two"
    other[app] = "marker"  // a write notifies before it returns, so the marker fires only after "two" was delivered

    awaitEquals(1) { marker.get() }
    assertEquals("one", seen.get())  // the disposed listener did not see "two"
  }

  @Test
  fun `a slotted key listener fires on slot data and on a current-selection switch`(): Unit = runBlocking {
    val seen = AtomicReference<String?>(null)
    style.addListener(disposable, app) { _, ctx -> seen.set(style[ctx]) }

    style[app.accessFor(style)] = "kt-style"  // accessFor pins the current selection, kotlin → the kotlin slot
    awaitEquals("kt-style") { seen.get() }

    langCurrent[app] = "java"  // discriminator switch re-points style at the (empty) java slot → default
    awaitEquals("none") { seen.get() }

    style[app.access { this[langCurrent] = "java" }] = "java-style"  // fill the java slot, now the current one
    awaitEquals("java-style") { seen.get() }
  }

  @Test
  fun `a contextual listener ignores other keys and changes outside its context chain`(): Unit = runBlocking {
    val fired = AtomicInteger(0)
    val sentinel = AtomicInteger(0)
    plain.addListener(disposable, app) { _, _ -> fired.incrementAndGet() }
    other.addListener(disposable, app) { _, _ -> sentinel.incrementAndGet() }

    plain[SettingsContext.project(project)] = "in-project"  // the watched key, but a narrower context not in app's chain
    other[app] = "sentinel"                                 // an unrelated key at the watched context

    // A write notifies before it returns, so the sentinel fires only after the project write was delivered.
    awaitEquals(1) { sentinel.get() }
    assertEquals(0, fired.get())  // neither the unrelated key nor the out-of-chain context fired the watcher
  }

  @Test
  fun `changing one key in a one-level slot notifies only that key`(): Unit = runBlocking {
    val c = seedMultidimAndListen()
    assertOnlyChangedFires(c, k1a, "k1a-changed")
  }

  @Test
  fun `changing one key in a two-level slot notifies only that key`(): Unit = runBlocking {
    val c = seedMultidimAndListen()
    assertOnlyChangedFires(c, k2a, "k2a-changed")
  }

  @Test
  fun `a slot-pinned listener fires on its slot, not on a sibling slot or a current-selection switch`(): Unit = runBlocking {
    d1[app] = "s1"
    k1a[app.access { this[d1] = "s1" }] = "s1-0"
    k1a[app.access { this[d1] = "s2" }] = "s2-0"
    assertEquals("s2-0", k1a[app.access { this[d1] = "s2" }])

    val pinned = app.access { this[d1] = "s2" }
    val fires = AtomicInteger(0)
    val sentinelFires = AtomicInteger(0)
    k1a.addListener(disposable, pinned) { _, _ -> fires.incrementAndGet() }
    sentinel.addListener(disposable, app) { _, _ -> sentinelFires.incrementAndGet() }

    k1a[pinned] = "s2-1"                               // change the pinned slot
    awaitEquals(1) { fires.get() }                     // → fires

    d1[app] = "s2"                                     // switch the current selection to the pinned slot
    k1a[app.access { this[d1] = "s1" }] = "s1-1"       // change the sibling slot

    quiesce(sentinelFires)
    assertEquals(1, fires.get())  // neither the selection switch nor the sibling-slot change fired the pinned watch
  }

  @Test
  fun `a nested slot-pinned listener fires on its slot, not on a sibling slot or a current-selection switch`(): Unit = runBlocking {
    dOuter[app] = "o1"; dInner[app] = "i1"
    k2a[app.access { this[dOuter] = "o1"; this[dInner] = "i1" }] = "o1i1-0"
    k2a[app.access { this[dOuter] = "o2"; this[dInner] = "i2" }] = "o2i2-0"
    assertEquals("o2i2-0", k2a[app.access { this[dOuter] = "o2"; this[dInner] = "i2" }])

    val pinned = app.access { this[dOuter] = "o2"; this[dInner] = "i2" }
    val fires = AtomicInteger(0)
    val sentinelFires = AtomicInteger(0)
    k2a.addListener(disposable, pinned) { _, _ -> fires.incrementAndGet() }
    sentinel.addListener(disposable, app) { _, _ -> sentinelFires.incrementAndGet() }

    k2a[pinned] = "o2i2-1"                             // change the pinned nested slot
    awaitEquals(1) { fires.get() }                     // → fires

    dOuter[app] = "o2"; dInner[app] = "i2"             // switch both current selections to the pinned slot
    k2a[app.access { this[dOuter] = "o1"; this[dInner] = "i1" }] = "o1i1-1"  // change the sibling nested slot

    quiesce(sentinelFires)
    assertEquals(1, fires.get())  // only the pinned nested slot's own change fired the watch
  }

  /**
   * Waits until every notification in flight is delivered. Two sentinel writes land in two separate bursts. When the
   * second burst is delivered, the first burst and everything queued before it are delivered too.
   */
  private suspend fun quiesce(sentinelFires: AtomicInteger) {
    val base = sentinelFires.get()
    sentinel[app] = "quiesce-a-$base"
    awaitEquals(base + 1) { sentinelFires.get() }
    sentinel[app] = "quiesce-b-$base"
    awaitEquals(base + 2) { sentinelFires.get() }
  }

  @Test
  fun `listening on a map key is rejected`() {
    assertThrows<IllegalArgumentException> { m1.addListener(disposable) { _, _ -> } }
    assertThrows<IllegalArgumentException> { m1.addListener(disposable, app) { _, _ -> } }
  }

  /** Holds the fire counters of each child key, and the counter of a sentinel that fences a notification burst. */
  private class Counters {
    val contextual = HashMap<String, AtomicInteger>()
    val keyOnly = HashMap<String, AtomicInteger>()
    val sentinel = AtomicInteger(0)
  }

  /**
   * Writes the discriminators and every child key of the one-level and the two-level storages at the application.
   * Then registers a contextual and a key-only listener on every child key, and a sentinel listener. The listeners come
   * after the writes, so each one starts from a real baseline.
   *
   * @return the counters of the registered listeners
   */
  private suspend fun seedMultidimAndListen(): Counters {
    d1[app] = "s1"; dOuter[app] = "o1"; dInner[app] = "i1"
    k1a[app.accessFor(k1a)] = "k1a-0"; k1b[app.accessFor(k1b)] = "k1b-0"
    k2a[app.accessFor(k2a)] = "k2a-0"; k2b[app.accessFor(k2b)] = "k2b-0"
    assertEquals("k2b-0", k2b[app])

    val c = Counters()
    for (key in childKeys) {
      c.contextual[key.identity] = AtomicInteger(0)
      c.keyOnly[key.identity] = AtomicInteger(0)
      key.addListener(disposable, app) { changed, _ -> c.contextual.getValue(changed.identity).incrementAndGet() }
      key.addListener(disposable) { changed, _ -> c.keyOnly.getValue(changed.identity).incrementAndGet() }
    }
    sentinel.addListener(disposable, app) { _, _ -> c.sentinel.incrementAndGet() }
    return c
  }

  private suspend fun assertOnlyChangedFires(c: Counters, changed: SettingsKey<String>, newValue: String) {
    changed[app.accessFor(changed)] = newValue
    awaitEquals(1) { c.contextual.getValue(changed.identity).get() }  // the change's flush has started (its burst drained)
    // The sentinel write comes only now, so it lands in a later burst. Its delivery starts after the delivery of the
    // change is complete, with any wrong sibling notification. So the counters below are final.
    sentinel[app] = "ping"
    awaitEquals(1) { c.sentinel.get() }

    for (key in childKeys) {
      val expected = if (key == changed) 1 else 0
      assertEquals(expected, c.contextual.getValue(key.identity).get(), "contextual ${key.name}")
      assertEquals(expected, c.keyOnly.getValue(key.identity).get(), "key-only ${key.name}")
    }
  }

  @Test
  fun `a queued burst across layers fires a listener once with the final value`(): Unit = runBlocking {
    val fires = AtomicInteger(0)
    val seen = AtomicReference<String?>(null)
    burst.addListener(disposable, app) { _, ctx ->
      seen.set(burst[ctx])
      fires.incrementAndGet()
    }

    val release = holdPushConsumer()
    provider.seed(app, burst, "one", LayerLocality.TEAM)
    provider.seed(app, burst, "two", LayerLocality.LOCAL)
    provider.seed(app, burst, "three", LayerLocality.LOCAL)
    release.countDown()

    awaitEquals("three") { seen.get() }
    assertEquals(1, fires.get())
  }

  @Test
  fun `a value that flips back within a queued burst fires no listener`(): Unit = runBlocking {
    val flipFires = AtomicInteger(0)
    val sentinelFires = AtomicInteger(0)
    flip.addListener(disposable, app) { _, _ -> flipFires.incrementAndGet() }
    sentinel.addListener(disposable, app) { _, _ -> sentinelFires.incrementAndGet() }
    provider.seed(app, flip, "start")
    awaitEquals(1) { flipFires.get() }

    val release = holdPushConsumer()
    provider.seed(app, flip, "changed")
    provider.seed(app, flip, "start")
    provider.seed(app, sentinel, "fenced")  // folds in the same burst, so its fire proves the burst was delivered
    release.countDown()

    awaitEquals(1) { sentinelFires.get() }
    assertEquals(1, flipFires.get())  // only the fire for "start" before the burst
  }

  /**
   * Holds the single push consumer of the vault inside a listener until the returned latch opens. The pushes sent
   * meanwhile queue up, so the consumer folds them as one burst.
   *
   * @return the latch that releases the consumer
   */
  private fun holdPushConsumer(): CountDownLatch {
    val held = CountDownLatch(1)
    val release = CountDownLatch(1)
    consumerHolder.addListener(disposable, app) { _, _ ->
      held.countDown()
      release.await(30, TimeUnit.SECONDS)
    }
    provider.seed(app, consumerHolder, "held")
    assertTrue(held.await(30, TimeUnit.SECONDS))
    return release
  }
}
