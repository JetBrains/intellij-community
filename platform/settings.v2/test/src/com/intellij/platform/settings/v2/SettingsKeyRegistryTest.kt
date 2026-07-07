// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.platform.settings.v2.impl.SettingsKeyPromoter
import com.intellij.platform.settings.v2.impl.SettingsKeyRegistry
import com.intellij.platform.settings.v2.impl.withRegistryChecks
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ref.GCUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the registry of promoted keys. A mutation from another side carries an identity, and the registry turns it
 * back into the key. A write confirms its key in the registry, so a key that nobody promoted is reported once.
 */
@TestApplication
class SettingsKeyRegistryTest {
  companion object {
    private val OWNER = PluginId.getId("test.registry.owner")
    private val OTHER = PluginId.getId("test.registry.other")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val registry get() = SettingsKeyRegistry.getInstance()

  @Test
  fun `a promoted key resolves by identity`() {
    val key = SettingsKey("test.registry.promoted", default = "d").withTestPluginId(OWNER)

    assertTrue(registry.register(key, OWNER))

    assertSame(key, registry[key.identity])
  }

  @Test
  fun `the same key promoted twice is one entry`() {
    val key = SettingsKey("test.registry.twice", default = "d").withTestPluginId(OWNER)

    assertTrue(registry.register(key, OWNER))
    assertTrue(registry.register(key, OWNER))

    assertSame(key, registry[key.identity])
  }

  @Test
  fun `a second key with one identity is refused`() {
    // One owner cannot declare one name twice, so the clash needs two owners. The plugin id and the name of the
    // second key join into the name of the platform key.
    val first = SettingsKey("test.registry.dupe.name", default = "d")
    val second = SettingsKey("name", default = "d").withTestPluginId(PluginId.getId("test.registry.dupe"))
    assertEquals(first.identity, second.identity)
    assertTrue(registry.register(first, null))

    assertFalse(expectingErrors { registry.register(second, PluginId.getId("test.registry.dupe")) })

    assertSame(first, registry[first.identity])
  }

  @Test
  fun `a key of an unknown identity resolves to nothing`() {
    assertNull(registry["test.registry.never.promoted"])
  }

  @Test
  fun `a promoter registers the keys of its own plugin only`() {
    val own = SettingsKey("test.registry.own", default = "d").withTestPluginId(OWNER)
    val foreign = SettingsKey("test.registry.foreign", default = "d").withTestPluginId(OTHER)

    expectingErrors { registry.promote(promoterOf(own, foreign), DefaultPluginDescriptor(OWNER, null)) }

    assertSame(own, registry[own.identity])
    assertNull(registry[foreign.identity])
  }

  @Test
  fun `a promoter that throws leaves all its keys unknown`() {
    val registered = SettingsKey("test.registry.before.failure", default = "d").withTestPluginId(OWNER)
    val failing = object : SettingsKeyPromoter {
      override fun promote(register: (SettingsKey<*>) -> Unit) {
        register(registered)
        throw IllegalStateException("promoter failure")
      }
    }

    expectingErrors { registry.promote(failing, DefaultPluginDescriptor(OWNER, null)) }

    assertNull(registry[registered.identity])
  }

  @Test
  fun `a cancellation in a promoter is logged, and all its keys stay unknown`() {
    val registered = SettingsKey("test.registry.before.cancellation", default = "d").withTestPluginId(OWNER)

    val (_, warnings) = collectingWarnings { registry.promote(cancellingPromoterOf(registered), DefaultPluginDescriptor(OWNER, null)) }

    assertNull(registry[registered.identity])
    assertTrue(warnings.any { it.contains("$OWNER") }, "the cancellation is logged: $warnings")
  }

  @Test
  fun `a cancelled promoter does not stop the rebuild after an unload`(): Unit = runBlocking {
    registry.awaitReady()
    val lost = SettingsKey("test.registry.cancelled", default = "d").withTestPluginId(OWNER)
    val kept = SettingsKey("test.registry.kept.after.cancellation", default = "d").withTestPluginId(OWNER)
    val gone = SettingsKey("test.registry.gone.after.cancellation", default = "d").withTestPluginId(OWNER)
    val module = Disposer.newDisposable(disposable)
    collectingWarnings { registerPromoter(cancellingPromoterOf(lost), disposable) }
    registerPromoter(promoterOf(kept), disposable)
    registerPromoter(promoterOf(gone), module)

    collectingWarnings { Disposer.dispose(module) }

    assertSame(kept, registry[kept.identity])
    assertNull(registry[gone.identity])
    assertNull(registry[lost.identity])
  }

  @Test
  fun `the registry gets ready when discovery starts at once`(): Unit = runBlocking {
    val eagerRegistry = SettingsKeyRegistry(this + Dispatchers.Unconfined)

    withTimeout(10.seconds) { eagerRegistry.awaitReady() }
  }

  @Test
  fun `the registry holds its keys strongly`() {
    val reference = registerUnreferencedKey()

    GCUtil.tryGcSoftlyReachableObjects()

    val key = reference.get()
    assertNotNull(key, "the registry keeps the key")
    assertSame(key, registry[key!!.identity])
  }

  @Test
  fun `a write waits for the registry to be ready, then checks its keys`(): Unit = runBlocking {
    registry.awaitReady()
    val key = SettingsKey("test.registry.awaited", default = "d").withTestPluginId(OWNER)
    val discovery = CountDownLatch(1)
    // The first call comes at once from the application registry. The second one is the discovery of the fresh registry.
    val calls = AtomicInteger()
    registerPromoter(object : SettingsKeyPromoter {
      override fun promote(register: (SettingsKey<*>) -> Unit) {
        if (calls.incrementAndGet() == 2) discovery.await()
        register(key)
      }
    }, disposable)
    try {
      val freshRegistry = SettingsKeyRegistry(this + Dispatchers.Default)
      val confirmed = async { freshRegistry.confirmForWrite(key) }

      assertNull(withTimeoutOrNull(200.milliseconds) { confirmed.await() }, "the write waits for the discovery")

      discovery.countDown()
      assertTrue(withTimeout(10.seconds) { confirmed.await() })
      assertTrue(freshRegistry.holds(key))
    }
    finally {
      discovery.countDown()
    }
  }

  @Test
  fun `an unloaded promoter takes its keys along, and its plugin keeps the rest`(): Unit = runBlocking {
    registry.awaitReady()
    val kept = SettingsKey("test.registry.kept", default = "d").withTestPluginId(OWNER)
    val gone = SettingsKey("test.registry.gone", default = "d").withTestPluginId(OWNER)
    // Two promoters of one plugin, as a plugin has one per module, and the module of the second one unloads.
    val module = Disposer.newDisposable(disposable)
    registerPromoter(promoterOf(kept), disposable)
    registerPromoter(promoterOf(gone), module)
    assertSame(gone, registry[gone.identity])

    Disposer.dispose(module)

    assertSame(kept, registry[kept.identity])
    assertNull(registry[gone.identity])
  }

  @Test
  fun `a write of a key nobody promoted registers it`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(TestLayerProvider()), disposable)
    val key = SettingsKey("test.registry.unpromoted", default = "d")
    val context = SettingsContext.app()

    val (_, errors) = collectingErrors { runBlocking { withRegistryChecks { key[context] = "written" } } }

    assertEquals("written", key[context])
    assertSame(key, registry[key.identity])
    assertTrue(errors.any { it.contains(key.identity) }, "the missing promotion is logged: $errors")
  }

  @Test
  fun `a write of a key of an unloaded module registers nothing`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(TestLayerProvider()), disposable)
    val key = declareAsPlugin("test.registry.unloaded", OWNER, PluginAwareClassLoader.UNLOAD_IN_PROGRESS)
    val context = SettingsContext.app()

    val (_, errors) = collectingErrors { runBlocking { withRegistryChecks { key[context] = "written" } } }

    assertEquals("written", key[context])
    assertNull(registry[key.identity])
    assertTrue(errors.any { it.contains(key.identity) }, "the unloaded module is logged: $errors")
  }

  @Test
  fun `a read does not consult the registry`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(TestLayerProvider()), disposable)
    val key = SettingsKey("test.registry.read", default = "d")

    val (_, errors) = collectingErrors { runBlocking { withRegistryChecks { assertEquals("d", key[SettingsContext.app()]) } } }

    assertEquals(emptyList<String>(), errors)
    assertNull(registry[key.identity])
  }

  @Test
  fun `a write of a key whose identity is taken fails`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(TestLayerProvider()), disposable)
    val promoted = SettingsKey("test.registry.taken.name", default = "d")
    val impostor = SettingsKey("name", default = "d").withTestPluginId(PluginId.getId("test.registry.taken"))
    registry.register(promoted, null)

    expectingErrors {
      assertThrows<IllegalStateException> {
        runBlocking { withRegistryChecks { impostor[SettingsContext.app()] = "x" } }
      }
    }

    assertEquals("d", promoted[SettingsContext.app()])
  }

  /** Loads [promoter] as an extension of [OWNER], until [parent] is disposed. */
  private fun registerPromoter(promoter: SettingsKeyPromoter, parent: Disposable) {
    SettingsKeyPromoter.EP_NAME.point.registerExtension(promoter, DefaultPluginDescriptor(OWNER, null), parent)
  }

  /** Returns a promoter that registers [keys] and then throws a cancellation of its own. */
  private fun cancellingPromoterOf(vararg keys: SettingsKey<*>): SettingsKeyPromoter = object : SettingsKeyPromoter {
    override fun promote(register: (SettingsKey<*>) -> Unit) {
      keys.forEach(register)
      throw CancellationException("promoter cancelled")
    }
  }

  /** Returns a promoter that registers [keys]. */
  private fun promoterOf(vararg keys: SettingsKey<*>): SettingsKeyPromoter = object : SettingsKeyPromoter {
    override fun promote(register: (SettingsKey<*>) -> Unit) {
      keys.forEach(register)
    }
  }

  /**
   * Registers a platform key, so that only the registry holds it.
   *
   * @return a weak reference to the key, which the garbage collector clears once the registry lets the key go
   */
  private fun registerUnreferencedKey(): WeakReference<SettingsKey<*>> {
    val key = SettingsKey("test.registry.held.strongly", default = "d")
    assertTrue(registry.register(key, null))
    return WeakReference(key)
  }

  /**
   * Runs [block] while a logged error is expected, since the registry reports a misuse and carries on.
   *
   * @return the result of [block]
   */
  private fun <T> expectingErrors(block: () -> T): T = collectingErrors(block).first
}
